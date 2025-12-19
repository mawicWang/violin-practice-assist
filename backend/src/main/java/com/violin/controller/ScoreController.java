package com.violin.controller;

import com.violin.entity.Score;
import com.violin.service.ScoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/scores")
@CrossOrigin(origins = "*")
public class ScoreController {

    @Autowired
    private ScoreService scoreService;

    @GetMapping
    public List<Score> getAllScores() {
        return scoreService.getAllScores();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Score> getScore(@PathVariable Long id) {
        return scoreService.getScoreById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/upload")
    public ResponseEntity<Score> uploadScore(@RequestParam("title") String title,
                                             @RequestParam("file") MultipartFile file) {
        try {
            Score score = scoreService.uploadScore(title, file);
            return ResponseEntity.ok(score);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<String> getScoreContent(@PathVariable Long id) {
        try {
            String content = scoreService.getXmlContent(id);
            if (content != null) {
                return ResponseEntity.ok(content);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
