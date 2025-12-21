package com.violin.service;

import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class SystemProcessExecutor implements ProcessExecutor {
    @Override
    public int execute(List<String> command, int timeoutSeconds) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        process.getInputStream().transferTo(System.out);
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return -1; // Time out
        }
        return process.exitValue();
    }
}
