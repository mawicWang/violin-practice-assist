package com.violin.service;

import com.violin.entity.Score;
import com.violin.repository.ScoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ScoreServiceTest {

    @Mock
    private ScoreRepository scoreRepository;

    @InjectMocks
    private ScoreService scoreService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Set storage location just in case it is needed, though deleteScore doesn't use it directly from properties
        ReflectionTestUtils.setField(scoreService, "storageLocation", "storage");
    }

    @Test
    void testDeleteScore() throws IOException {
        Long scoreId = 1L;
        Score score = new Score();
        score.setId(scoreId);

        // Create a temporary file to simulate the score image
        Path tempFile = Files.createTempFile("test-score", ".png");
        score.setOriginalImagePath(tempFile.toString());

        when(scoreRepository.findById(scoreId)).thenReturn(Optional.of(score));

        // Execute delete
        scoreService.deleteScore(scoreId);

        // Verify repository delete was called
        verify(scoreRepository, times(1)).deleteById(scoreId);

        // Verify file was deleted
        assertFalse(Files.exists(tempFile), "File should be deleted");
    }

    @Test
    void testDeleteScore_NotFound() {
        Long scoreId = 99L;
        when(scoreRepository.findById(scoreId)).thenReturn(Optional.empty());

        scoreService.deleteScore(scoreId);

        verify(scoreRepository, never()).deleteById(any());
    }
}
