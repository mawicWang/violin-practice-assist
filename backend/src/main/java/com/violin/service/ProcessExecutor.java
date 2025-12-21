package com.violin.service;

import java.io.IOException;
import java.util.List;

public interface ProcessExecutor {
    /**
     * Executes a system command.
     * @param command The command and arguments.
     * @param timeoutSeconds Timeout in seconds.
     * @return The exit code, or -1 if timed out.
     * @throws IOException If an I/O error occurs.
     * @throws InterruptedException If the current thread is interrupted.
     */
    int execute(List<String> command, int timeoutSeconds) throws IOException, InterruptedException;
}
