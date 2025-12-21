package com.violin.service;

import com.violin.entity.Score;
import com.violin.repository.ScoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class focused on the processing logic (commands) in ScoreService.
 */
class ScoreServiceProcessingTest {

    @Mock
    private ScoreRepository scoreRepository;

    @Mock
    private ProcessExecutor processExecutor;

    @InjectMocks
    private ScoreService scoreService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(scoreService, "storageLocation", "storage_test");
    }

    // Since processImage is private and runs in a thread, we can't test it directly easily without reflection or refactoring.
    // However, we can use reflection to invoke it synchronously for testing purposes.
    private void invokeProcessImage(Long scoreId, String imagePath) throws Exception {
        java.lang.reflect.Method method = ScoreService.class.getDeclaredMethod("processImage", Long.class, String.class);
        method.setAccessible(true);
        method.invoke(scoreService, scoreId, imagePath);
    }

    @Test
    void testProcessImage_FullFlow() throws Exception {
        Long scoreId = 1L;
        String imagePath = "test_image.png";

        // Mock Score
        Score score = new Score();
        score.setId(scoreId);
        when(scoreRepository.findById(scoreId)).thenReturn(Optional.of(score));

        // Mock ProcessExecutor to succeed for oemer
        // We need to simulate oemer creating an output file in the temp dir.
        // Since the temp dir path is generated inside the method, we have a problem.
        // We can't know the path to create the file.

        // This makes unit testing the full flow with file system side effects tricky.
        // Option 1: Mock Files.createTempDirectory? (Static mock, complex)
        // Option 2: Refactor ScoreService to make temp dir strategy injectable.
        // Option 3: Accept that we can't verify the file detection part easily with unit tests,
        //           and just verify that the commands are executed.

        // But ScoreService checks for the existence of the musicxml file.
        // If it's not there, it aborts.

        // Let's refactor ScoreService to be more testable? Or use a relaxed approach where we just check the calls we can.
        // If I can't create the file, the second step (xml2abc) won't run.

        // Actually, I can use a Mockito Answer to create the file!
        // When processExecutor.execute is called with "oemer", I can look at the arguments (argument 3 is output dir)
        // and create a dummy file there.

        when(processExecutor.execute(anyList(), anyInt())).thenAnswer(invocation -> {
            List<String> command = invocation.getArgument(0);
            if (command.get(0).equals("oemer")) {
                // Creates dummy musicxml in the output dir
                String outDir = command.get(3);
                File dir = new File(outDir);
                dir.mkdirs();
                new File(dir, "output.musicxml").createNewFile();
                return 0; // Success
            }
            if (command.get(0).equals("python")) {
                // xml2abc
                String outFile = command.get(3);
                Files.writeString(Path.of(outFile), "X:1\nT:Test Score\nK:C\nC D E F|");
                return 0;
            }
            return -1;
        });

        invokeProcessImage(scoreId, imagePath);

        // Verify oemer call
        ArgumentCaptor<List<String>> cmdCaptor = ArgumentCaptor.forClass(List.class);
        verify(processExecutor, atLeastOnce()).execute(cmdCaptor.capture(), anyInt());

        List<List<String>> allValues = cmdCaptor.getAllValues();
        // Should have 2 calls: oemer and python
        boolean oemerCalled = allValues.stream().anyMatch(cmd -> cmd.get(0).equals("oemer"));
        boolean pythonCalled = allValues.stream().anyMatch(cmd -> cmd.get(0).equals("python"));

        assertTrue(oemerCalled, "Oemer should be called");
        assertTrue(pythonCalled, "Python (xml2abc) should be called");

        // Verify score update
        ArgumentCaptor<Score> scoreCaptor = ArgumentCaptor.forClass(Score.class);
        verify(scoreRepository, atLeastOnce()).save(scoreCaptor.capture());
        Score savedScore = scoreCaptor.getValue();
        assertTrue(savedScore.getAbcContent().contains("X:1"), "ABC content should be updated");
    }

    @Test
    void testProcessImage_MusicXml() throws Exception {
        Long scoreId = 2L;
        String filePath = "test_score.musicxml";

        // Mock Score
        Score score = new Score();
        score.setId(scoreId);
        when(scoreRepository.findById(scoreId)).thenReturn(Optional.of(score));

        // Mock ProcessExecutor
        when(processExecutor.execute(anyList(), anyInt())).thenAnswer(invocation -> {
            List<String> command = invocation.getArgument(0);
            if (command.get(0).equals("oemer")) {
                fail("Oemer should not be called for MusicXML");
                return -1;
            }
            if (command.get(0).equals("python")) {
                // xml2abc
                String outFile = command.get(3);
                // Ensure the input file passed is the uploaded one
                String inFile = command.get(4);
                assertEquals(new File(filePath).getAbsolutePath(), inFile, "Input to xml2abc should be the uploaded file");

                Files.writeString(Path.of(outFile), "X:1\nT:XML Score\n");
                return 0;
            }
            return -1;
        });

        invokeProcessImage(scoreId, filePath);

        // Verify python call
        ArgumentCaptor<List<String>> cmdCaptor = ArgumentCaptor.forClass(List.class);
        verify(processExecutor, times(1)).execute(cmdCaptor.capture(), anyInt());
        List<String> command = cmdCaptor.getValue();
        assertEquals("python", command.get(0));

        // Verify score update
        ArgumentCaptor<Score> scoreCaptor = ArgumentCaptor.forClass(Score.class);
        verify(scoreRepository, atLeastOnce()).save(scoreCaptor.capture());
        Score savedScore = scoreCaptor.getValue();
        assertTrue(savedScore.getAbcContent().contains("T:XML Score"), "ABC content should be updated from XML");
    }

    @Test
    void testProcessImage_Abc() throws Exception {
        Long scoreId = 3L;
        // We need a real file for ABC reading because the code uses Files.readString(Paths.get(imagePath))
        // So we create a temp file.
        Path tempAbc = Files.createTempFile("test_score", ".abc");
        Files.writeString(tempAbc, "X:1\nT:ABC Score\n");
        String filePath = tempAbc.toAbsolutePath().toString();

        // Mock Score
        Score score = new Score();
        score.setId(scoreId);
        when(scoreRepository.findById(scoreId)).thenReturn(Optional.of(score));

        // Mock ProcessExecutor - should not be called
        when(processExecutor.execute(anyList(), anyInt())).thenThrow(new RuntimeException("Should not execute commands"));

        invokeProcessImage(scoreId, filePath);

        verify(processExecutor, never()).execute(anyList(), anyInt());

        // Verify score update
        ArgumentCaptor<Score> scoreCaptor = ArgumentCaptor.forClass(Score.class);
        verify(scoreRepository, atLeastOnce()).save(scoreCaptor.capture());
        Score savedScore = scoreCaptor.getValue();
        assertTrue(savedScore.getAbcContent().contains("T:ABC Score"), "ABC content should be read directly");

        Files.delete(tempAbc);
    }
}
