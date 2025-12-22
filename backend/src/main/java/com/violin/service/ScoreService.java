package com.violin.service;

import com.violin.entity.Score;
import com.violin.repository.ScoreRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.concurrent.TimeUnit;
import java.io.File;
import java.util.stream.Stream;
import java.util.Comparator;

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
        // If updating an existing score, perform a smart update to avoid overwriting fields with null
        if (score.getId() != null) {
            Optional<Score> existingOpt = scoreRepository.findById(score.getId());
            if (existingOpt.isPresent()) {
                Score existing = existingOpt.get();
                boolean contentChanged = false;

                if (score.getAbcContent() != null) {
                     if (!score.getAbcContent().equals(existing.getAbcContent()) || existing.getXmlContent() == null) {
                         existing.setAbcContent(score.getAbcContent());
                         contentChanged = true;
                     }
                }

                if (score.getTitle() != null) {
                    existing.setTitle(score.getTitle());
                }

                if (contentChanged) {
                    convertAbcToXml(existing);
                }

                return scoreRepository.save(existing);
            }
        }
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

        String originalFilename = file.getOriginalFilename();
        String filename = System.currentTimeMillis() + "_" + originalFilename;
        Path filePath = uploadPath.resolve(filename);
        Files.copy(file.getInputStream(), filePath);

        Score score = new Score();
        score.setTitle(title);
        score.setOriginalImagePath(filePath.toString());

        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
             extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        }

        if ("abc".equals(extension)) {
             String abcContent = Files.readString(filePath);
             score.setAbcContent(abcContent);
             convertAbcToXml(score);
             score = scoreRepository.save(score);
        } else if ("xml".equals(extension) || "musicxml".equals(extension)) {
             String xmlContent = Files.readString(filePath);
             score.setXmlContent(xmlContent);
             score.setAbcContent("T: Processing...\nM: 4/4\nK: C\n% Please wait for MusicXML conversion...");
             score = scoreRepository.save(score);
             final Long scoreId = score.getId();
             final String xmlPath = filePath.toAbsolutePath().toString();
             new Thread(() -> processMusicXml(scoreId, new File(xmlPath))).start();
        } else {
             score.setAbcContent("T: Processing...\nM: 4/4\nK: C\n% Please wait for OMR processing...");
             score = scoreRepository.save(score);
             // Run OMR in background
             final Long scoreId = score.getId();
             final String imagePath = filePath.toAbsolutePath().toString();
             new Thread(() -> processImage(scoreId, imagePath)).start();
        }

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

            // Read and save the generated MusicXML
            String xmlContent = Files.readString(musicXmlFile.toPath());
            updateScoreXmlContent(scoreId, xmlContent);

            processMusicXml(scoreId, musicXmlFile);

            // Cleanup temp dir (optional, maybe keep for debug)
            // FileUtils.deleteDirectory(tempDir.toFile());

        } catch (Exception e) {
            log.error("OMR Process Exception", e);
             updateScoreContent(scoreId, "T: Error\n% OMR processing failed: " + e.getMessage());
        }
    }

    private void processMusicXml(Long scoreId, File musicXmlFile) {
        try {
            log.info("Starting MusicXML processing for score {}", scoreId);
            // We use the same temp dir logic or create new?
            // Better create a new temp dir for xml2abc if we want to be safe or reuse if passed.
            // For simplicity and since we don't pass tempDir around, create a new one.
            Path tempDir = Files.createTempDirectory("xml2abc_" + scoreId);

            // 2. Run xml2abc.py
            // python tools/xml2abc.py -o <output_dir> <input.musicxml>
            // Note: xml2abc -o expects a directory and generates a file with .abc extension

            // Resolve tool path (handle running from backend dir or root)
            String toolScript = "tools/xml2abc.py";
            if (!new File(toolScript).exists() && new File("../" + toolScript).exists()) {
                toolScript = "../" + toolScript;
            }

            ProcessBuilder abcPb = new ProcessBuilder("python", toolScript, "-o", tempDir.toString(), musicXmlFile.getAbsolutePath());
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

            // Determine the generated abc file path
            // xml2abc uses os.path.splitext to replace extension with .abc
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
                // Fallback: list .abc files in tempDir
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
            log.info("MusicXML to ABC finished for score {}", scoreId);

        } catch (Exception e) {
            log.error("MusicXML Process Exception", e);
             updateScoreContent(scoreId, "T: Error\n% MusicXML processing failed: " + e.getMessage());
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

    private void updateScoreXmlContent(Long scoreId, String xmlContent) {
        Optional<Score> scoreOpt = scoreRepository.findById(scoreId);
        if (scoreOpt.isPresent()) {
            Score s = scoreOpt.get();
            s.setXmlContent(xmlContent);
            scoreRepository.save(s);
        }
    }

    private void convertAbcToXml(Score score) {
        Path tempDir = null;
        try {
            log.info("Starting ABC to MusicXML conversion for score {}", score.getId());
            tempDir = Files.createTempDirectory("abc2xml_" + (score.getId() != null ? score.getId() : "new"));
            File abcFile = tempDir.resolve("score.abc").toFile();
            Files.writeString(abcFile.toPath(), score.getAbcContent());

            // python tools/abc2xml.py -o <output_dir> <input.abc>
            String toolScript = "tools/abc2xml.py";
            if (!new File(toolScript).exists() && new File("../" + toolScript).exists()) {
                toolScript = "../" + toolScript;
            }

            // Use python3 if available, fallback to python? Or assume python3 is the requirement.
            // The environment requirement says "Python 3 with oemer installed".
            ProcessBuilder pb = new ProcessBuilder("python", toolScript, "-o", tempDir.toString(), abcFile.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            process.getInputStream().transferTo(System.out);
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);

            if (!finished || process.exitValue() != 0) {
                log.error("abc2xml failed");
                return;
            }

            File[] xmlFiles = tempDir.toFile().listFiles((d, name) -> name.endsWith(".xml") || name.endsWith(".musicxml"));
            if (xmlFiles != null && xmlFiles.length > 0) {
                String xmlContent = Files.readString(xmlFiles[0].toPath());
                score.setXmlContent(xmlContent);
            } else {
                log.error("No MusicXML output found in {}", tempDir);
            }

        } catch (Exception e) {
            log.error("ABC to MusicXML conversion failed", e);
        } finally {
            if (tempDir != null) {
                try (Stream<Path> walk = Files.walk(tempDir)) {
                    walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                } catch (IOException e) {
                    log.warn("Failed to clean up temp dir: " + tempDir, e);
                }
            }
        }
    }
}
