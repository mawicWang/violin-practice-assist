package com.violin.service;

import com.violin.entity.Score;
import com.violin.repository.ScoreRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

@Service
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

    public Score uploadScore(String title, MultipartFile file) throws IOException {
        Path uploadPath = Paths.get(storageLocation);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String filename = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        Path filePath = uploadPath.resolve(filename);
        Files.copy(file.getInputStream(), filePath);

        Score score = new Score();
        score.setTitle(title);

        if (filename.endsWith(".xml") || filename.endsWith(".musicxml") || filename.endsWith(".mxl")) {
            score.setMusicXmlPath(filePath.toString());
            score.setStatus("READY");
        } else {
            score.setOriginalImagePath(filePath.toString());
            score.setStatus("PROCESSING");
            mockOmrProcess(score);
        }

        return scoreRepository.save(score);
    }

    private void mockOmrProcess(Score score) {
        // Simulate OMR processing delay
        new Thread(() -> {
            try {
                Thread.sleep(2000); // Simulate processing time

                // Copy sample XML to storage
                String newFileName = System.currentTimeMillis() + "_mock_result.xml";
                Path targetPath = Paths.get(storageLocation).resolve(newFileName);

                try (var inputStream = getClass().getResourceAsStream("/sample.xml")) {
                    if (inputStream != null) {
                        Files.copy(inputStream, targetPath);

                        score.setMusicXmlPath(targetPath.toString());
                        score.setStatus("READY");
                        scoreRepository.save(score);
                    } else {
                         System.err.println("Sample XML not found!");
                         score.setStatus("FAILED");
                         scoreRepository.save(score);
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
                score.setStatus("FAILED");
                scoreRepository.save(score);
            }
        }).start();

        // Initial status
        score.setStatus("PROCESSING");
    }

    public String getXmlContent(Long id) throws IOException {
        Optional<Score> scoreOpt = scoreRepository.findById(id);
        if (scoreOpt.isPresent()) {
            Score score = scoreOpt.get();
            if (score.getMusicXmlPath() != null) {
                return Files.readString(Paths.get(score.getMusicXmlPath()));
            }
        }
        return null;
    }
}
