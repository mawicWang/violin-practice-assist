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

    @Autowired
    private ProcessExecutor processExecutor;

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

    public void deleteScore(Long id) {
        Optional<Score> scoreOpt = scoreRepository.findById(id);
        if (scoreOpt.isPresent()) {
            Score score = scoreOpt.get();
            // Delete file
            if (score.getOriginalImagePath() != null) {
                try {
                    Files.deleteIfExists(Paths.get(score.getOriginalImagePath()));
                } catch (IOException e) {
                    log.error("Failed to delete file: " + score.getOriginalImagePath(), e);
                }
            }
            scoreRepository.deleteById(id);
        }
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

    private void processImage(Long scoreId, String filePath) {
        try {
            log.info("Starting processing for score {}, file: {}", scoreId, filePath);
            Path tempDir = Files.createTempDirectory("omr_" + scoreId);
            String lowerPath = filePath.toLowerCase();

            File musicXmlFile = null;

            if (lowerPath.endsWith(".abc")) {
                // Direct ABC upload
                String abcContent = Files.readString(Paths.get(filePath));
                updateScoreContent(scoreId, abcContent);
                log.info("ABC loaded directly for score {}", scoreId);
                return;
            } else if (lowerPath.endsWith(".xml") || lowerPath.endsWith(".musicxml")) {
                // Direct MusicXML upload
                musicXmlFile = new File(filePath);
            } else {
                // Image upload - run OMR
                // 1. Run oemer
                // oemer <img_path> -o <output_dir>
                int oemerExitCode = processExecutor.execute(List.of("oemer", filePath, "-o", tempDir.toString()), 120);

                if (oemerExitCode != 0) {
                     log.error("Oemer failed or timed out");
                     updateScoreContent(scoreId, "T: Error\n% OMR processing failed (oemer).");
                     return;
                }

                // Oemer outputs a file ending with .musicxml in the output directory
                File[] musicXmlFiles = tempDir.toFile().listFiles((d, name) -> name.endsWith(".musicxml"));
                if (musicXmlFiles == null || musicXmlFiles.length == 0) {
                    log.error("No MusicXML output found in {}", tempDir);
                    updateScoreContent(scoreId, "T: Error\n% OMR processing failed (no output).");
                    return;
                }
                musicXmlFile = musicXmlFiles[0];
            }

            // 2. Run xml2abc.py (if we have a MusicXML file from upload or OMR)
            if (musicXmlFile != null) {
                // python tools/xml2abc.py -o <output.abc> <input.musicxml>
                String abcPath = tempDir.resolve("output.abc").toString();

                // Resolve tool path (handle running from backend dir or root)
                String toolScript = "tools/xml2abc.py";
                if (!new File(toolScript).exists() && new File("../" + toolScript).exists()) {
                    toolScript = "../" + toolScript;
                }

                int abcExitCode = processExecutor.execute(List.of("python", toolScript, "-o", abcPath, musicXmlFile.getAbsolutePath()), 30);

                if (abcExitCode != 0) {
                    log.error("xml2abc failed");
                     updateScoreContent(scoreId, "T: Error\n% OMR processing failed (xml2abc).");
                     return;
                }

                String abcContent = Files.readString(Paths.get(abcPath));
                updateScoreContent(scoreId, abcContent);
                log.info("Conversion finished for score {}", scoreId);
            }

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
