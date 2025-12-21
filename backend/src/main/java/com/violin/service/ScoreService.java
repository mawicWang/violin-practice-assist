package com.violin.service;

import com.violin.entity.Score;
import com.violin.repository.ScoreRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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
        score.setAbcContent("T: Processing...\nM: 4/4\nK: C\n% Please wait for OMR processing...");
        score = scoreRepository.save(score);

        final Long scoreId = score.getId();
        final String savedFilePath = filePath.toAbsolutePath().toString();
        new Thread(() -> processImage(scoreId, savedFilePath)).start();

        return score;
    }

    private void processImage(Long scoreId, String filePath) {
        try {
            // Log processing start
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
                int oemerExitCode = processExecutor.execute(List.of("oemer", filePath, "-o", tempDir.toString()), 120);

                if (oemerExitCode != 0) {
                     log.error("Oemer failed or timed out");
                     updateScoreContent(scoreId, "T: Error\n% OMR processing failed (oemer).");
                     return;
                }

                File[] musicXmlFiles = tempDir.toFile().listFiles((d, name) -> name.endsWith(".musicxml"));
                if (musicXmlFiles == null || musicXmlFiles.length == 0) {
                    log.error("No MusicXML output found in {}", tempDir);
                    updateScoreContent(scoreId, "T: Error\n% OMR processing failed (no output).");
                    return;
                }
                musicXmlFile = musicXmlFiles[0];
            }

            if (musicXmlFile != null) {
                String toolScript = "tools/xml2abc.py";
                if (!new File(toolScript).exists() && new File("../" + toolScript).exists()) {
                    toolScript = "../" + toolScript;
                }

                // xml2abc -o <dir>
                int abcExitCode = processExecutor.execute(List.of("python", toolScript, "-o", tempDir.toString(), musicXmlFile.getAbsolutePath()), 30);

                if (abcExitCode != 0) {
                    log.error("xml2abc failed");
                     updateScoreContent(scoreId, "T: Error\n% OMR processing failed (xml2abc).");
                     return;
                }

                // Determine the generated abc file path
                String xmlFilename = musicXmlFile.getName();
                int lastDotIndex = xmlFilename.lastIndexOf('.');
                String abcFilename;
                if (lastDotIndex != -1) {
                    abcFilename = xmlFilename.substring(0, lastDotIndex) + ".abc";
                } else {
                    abcFilename = xmlFilename + ".abc";
                }
                Path abcPath = tempDir.resolve(abcFilename);

                if (!Files.exists(abcPath)) {
                    File[] abcFiles = tempDir.toFile().listFiles((d, name) -> name.endsWith(".abc"));
                    if (abcFiles != null && abcFiles.length > 0) {
                         abcPath = abcFiles[0].toPath();
                    } else {
                         log.error("No ABC output found in {}", tempDir);
                         updateScoreContent(scoreId, "T: Error\n% OMR processing failed (no abc output).");
                         return;
                    }
                }

                String abcContent = Files.readString(abcPath);
                updateScoreContent(scoreId, abcContent);
                log.info("Conversion finished for score {}", scoreId);
            }

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
