package com.violin.service;

import com.violin.entity.Score;
import com.violin.repository.ScoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ScoreServiceTest {

    @Mock
    private ScoreRepository scoreRepository;

    @InjectMocks
    private ScoreService scoreService;

    private Path tempStorage;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        // Set storage location to a temp dir
        tempStorage = Files.createTempDirectory("test-storage");
        ReflectionTestUtils.setField(scoreService, "storageLocation", tempStorage.toString());
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

    @Test
    void testUploadAbcFile() throws IOException {
        String abcContent = "X:1\nT:Test Score\nK:C\nCDEFG|";
        MockMultipartFile file = new MockMultipartFile("file", "test.abc", "text/plain", abcContent.getBytes(StandardCharsets.UTF_8));

        when(scoreRepository.save(any(Score.class))).thenAnswer(invocation -> {
            Score s = invocation.getArgument(0);
            s.setId(1L);
            return s;
        });

        Score result = scoreService.uploadAndRecognize("Test Title", file);

        assertNotNull(result);
        assertEquals("Test Title", result.getTitle());
        // For ABC files, the content should be read directly
        assertEquals(abcContent, result.getAbcContent());

        verify(scoreRepository, atLeastOnce()).save(any(Score.class));
    }

    @Test
    void testUploadImageFile() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", "fake image content".getBytes());

        when(scoreRepository.save(any(Score.class))).thenAnswer(invocation -> {
            Score s = invocation.getArgument(0);
            s.setId(1L);
            return s;
        });

        Score result = scoreService.uploadAndRecognize("Image Title", file);

        assertNotNull(result);
        assertTrue(result.getAbcContent().startsWith("T: Processing..."));
        assertTrue(result.getAbcContent().contains("OMR processing"));
    }

    @Test
    void testUploadMusicXmlFile() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "test.musicxml", "text/xml", "fake xml content".getBytes());

        when(scoreRepository.save(any(Score.class))).thenAnswer(invocation -> {
            Score s = invocation.getArgument(0);
            s.setId(1L);
            return s;
        });

        Score result = scoreService.uploadAndRecognize("XML Title", file);

        assertNotNull(result);
        assertTrue(result.getAbcContent().startsWith("T: Processing..."));
        assertTrue(result.getAbcContent().contains("MusicXML conversion"));
    }
}
