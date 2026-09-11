package autogradesys.grading;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Runs a bounded child process without invoking a shell. */
final class ProcessRunner {
    private ProcessRunner() {
    }

    static Result run(List<String> command, String input, Duration timeout)
            throws IOException, InterruptedException {
        return run(command, input, timeout, null);
    }

    static Result run(List<String> command, String input, Duration timeout, java.nio.file.Path workingDirectory)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        if (workingDirectory != null) {
            builder.directory(workingDirectory.toFile());
        }
        Process process = builder.start();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        Thread stdoutReader = copyAsync(process.getInputStream(), stdout, "stdout-reader");
        Thread stderrReader = copyAsync(process.getErrorStream(), stderr, "stderr-reader");
        Thread inputWriter = new Thread(() -> {
            try {
                if (input != null) {
                    process.getOutputStream().write(input.getBytes(StandardCharsets.UTF_8));
                }
            } catch (IOException ignored) {
                // The process may have exited before consuming its input.
            } finally {
                try {
                    process.getOutputStream().close();
                } catch (IOException ignored) {
                    // The process is already being terminated or has exited.
                }
            }
        }, "grader-input-writer");
        inputWriter.setDaemon(true);
        inputWriter.start();

        boolean finished;
        try {
            long timeoutMillis = Math.max(1L, timeout.toMillis());
            finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
            if (!finished) {
                terminate(process);
                process.getOutputStream().close();
            }
        } catch (InterruptedException interrupted) {
            terminate(process);
            Thread.currentThread().interrupt();
            throw interrupted;
        }

        stdoutReader.join(1_000L);
        stderrReader.join(1_000L);
        inputWriter.join(1_000L);
        int exitCode = finished ? process.exitValue() : -1;
        return new Result(exitCode, !finished,
                stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
    }

    private static Thread copyAsync(InputStream input, ByteArrayOutputStream output, String name) {
        Thread reader = new Thread(() -> {
            try (InputStream stream = input) {
                stream.transferTo(output);
            } catch (IOException ignored) {
                // Process termination can close a stream while it is being read.
            }
        }, name);
        reader.setDaemon(true);
        reader.start();
        return reader;
    }

    private static void terminate(Process process) throws InterruptedException {
        process.toHandle().descendants().forEach(handle -> handle.destroyForcibly());
        process.destroy();
        if (!process.waitFor(200L, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            process.waitFor(1_000L, TimeUnit.MILLISECONDS);
        }
        process.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
    }

    record Result(int exitCode, boolean timedOut, String stdout, String stderr) {
    }
}
