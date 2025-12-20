package com.violin.service;

import com.violin.entity.Score;
import com.violin.repository.ScoreRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.concurrent.TimeUnit;
import java.io.File;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ScoreService {

    @Autowired
    private ScoreRepository scoreRepository;

    @Value("${app.storage.location}")
    private String storageLocation;

    public List<Score> getAllScores() {
        return scoreRepository.findAll();
    }

    public Optional<Score> getScoreById(Long id) {
        return scoreRepository.findById(id);
    }

    public Score saveScore(Score score) {
        return scoreRepository.save(score);
    }

    public Score uploadAndRecognize(String title, MultipartFile file) throws IOException {
        Path uploadPath = Paths.get(storageLocation);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String filename = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        Path filePath = uploadPath.resolve(filename);
        Files.copy(file.getInputStream(), filePath);

        Score score = new Score();
        score.setTitle(title);
        score.setOriginalImagePath(filePath.toString());
        score.setAbcContent("T: Processing...\nM: 4/4\nK: C\n% Please wait for OMR processing..."); // Temporary content
        score = scoreRepository.save(score);

        // Run OMR in background
        final Long scoreId = score.getId();
        final String imagePath = filePath.toAbsolutePath().toString();
        new Thread(() -> processImage(scoreId, imagePath)).start();

        return score;
    }

    private void processImage(Long scoreId, String imagePath) {
        try {
            log.info("Starting OMR for score {}", scoreId);
            Path tempDir = Files.createTempDirectory("omr_" + scoreId);

            // 1. Run oemer
            // oemer <img_path> -o <output_dir>
            ProcessBuilder oemerPb = new ProcessBuilder("oemer", imagePath, "-o", tempDir.toString());
            oemerPb.redirectErrorStream(true);
            Process oemerProcess = oemerPb.start();
            // Read output to prevent blocking
            oemerProcess.getInputStream().transferTo(System.out);
            boolean oemerFinished = oemerProcess.waitFor(120, TimeUnit.SECONDS);

            if (!oemerFinished || oemerProcess.exitValue() != 0) {
                 log.error("Oemer failed or timed out");
                 updateScoreContent(scoreId, "T: Error\n% OMR processing failed (oemer).");
                 return;
            }

            // Oemer outputs a file ending with .musicxml in the output directory
            // The filename is usually the image filename + .musicxml (but checking dir is safer)
            File[] musicXmlFiles = tempDir.toFile().listFiles((d, name) -> name.endsWith(".musicxml"));
            if (musicXmlFiles == null || musicXmlFiles.length == 0) {
                log.error("No MusicXML output found in {}", tempDir);
                updateScoreContent(scoreId, "T: Error\n% OMR processing failed (no output).");
                return;
            }
            File musicXmlFile = musicXmlFiles[0];

            // 2. Run xml2abc.py
            // python tools/xml2abc.py -o <output.abc> <input.musicxml>
            String abcPath = tempDir.resolve("output.abc").toString();

            // Resolve tool path (handle running from backend dir or root)
            String toolScript = "tools/xml2abc.py";
            if (!new File(toolScript).exists() && new File("../" + toolScript).exists()) {
                toolScript = "../" + toolScript;
            }

            ProcessBuilder abcPb = new ProcessBuilder("python", toolScript, "-o", abcPath, musicXmlFile.getAbsolutePath());
            abcPb.redirectErrorStream(true);
            Process abcProcess = abcPb.start();
             // Read output to prevent blocking
            abcProcess.getInputStream().transferTo(System.out);
            boolean abcFinished = abcProcess.waitFor(30, TimeUnit.SECONDS);

            if (!abcFinished || abcProcess.exitValue() != 0) {
                log.error("xml2abc failed");
                 updateScoreContent(scoreId, "T: Error\n% OMR processing failed (xml2abc).");
                 return;
            }

            String abcContent = Files.readString(Paths.get(abcPath));
            updateScoreContent(scoreId, abcContent);
            log.info("OMR finished for score {}", scoreId);

            // Cleanup temp dir (optional, maybe keep for debug)
            // FileUtils.deleteDirectory(tempDir.toFile());

        } catch (Exception e) {
            log.error("OMR Process Exception", e);
             updateScoreContent(scoreId, "T: Error\n% OMR processing failed: " + e.getMessage());
        }
    }

    private void updateScoreContent(Long scoreId, String content) {
        Optional<Score> scoreOpt = scoreRepository.findById(scoreId);
        if (scoreOpt.isPresent()) {
            Score s = scoreOpt.get();
            s.setAbcContent(content);
            scoreRepository.save(s);
        }
    }
}
