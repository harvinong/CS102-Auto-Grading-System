package autogradesys.grading;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Runs a bounded child process without invoking a shell.
 *
 * <p>Arguments stay as a list so submitted paths and source text cannot be
 * reinterpreted as shell syntax.</p>
 */
final class ProcessRunner {
    private ProcessRunner() {
    }

    /** Runs a command with no working-directory override. */
    static Result run(List<String> command, String input, Duration timeout)
            throws IOException, InterruptedException {
        return run(command, input, timeout, null, 1024 * 1024L);
    }

    /**
     * Runs a command, captures both output streams, and enforces a deadline.
     *
     * <p>Output is drained concurrently so a noisy child cannot block waiting
     * for a pipe that the parent has not yet read.</p>
     */
    static Result run(List<String> command, String input, Duration timeout, java.nio.file.Path workingDirectory)
            throws IOException, InterruptedException {
        return run(command, input, timeout, workingDirectory, 1024 * 1024L);
    }

    static Result run(List<String> command, String input, Duration timeout,
            java.nio.file.Path workingDirectory, long outputLimitBytes)
            throws IOException, InterruptedException {
        if (outputLimitBytes <= 0) {
            throw new IllegalArgumentException("output limit must be positive");
        }
        ProcessBuilder builder = new ProcessBuilder(command);
        if (workingDirectory != null) {
            builder.directory(workingDirectory.toFile());
        }
        Process process = builder.start();
        Capture capture = new Capture(outputLimitBytes);
        Thread stdoutReader = copyAsync(process.getInputStream(), capture.stdout, capture, "stdout-reader");
        Thread stderrReader = copyAsync(process.getErrorStream(), capture.stderr, capture, "stderr-reader");
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
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            finished = false;
            while (!finished && !capture.limitExceeded()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    break;
                }
                finished = process.waitFor(Math.min(50L,
                        Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remaining))), TimeUnit.MILLISECONDS);
            }
            if (!finished) {
                // Kill the whole process tree: a timed-out submission must not survive grading.
                terminate(process);
                process.getOutputStream().close();
            }
        } catch (InterruptedException interrupted) {
            // Preserve cancellation while still cleaning up the child we started.
            terminate(process);
            Thread.currentThread().interrupt();
            throw interrupted;
        }

        stdoutReader.join(1_000L);
        stderrReader.join(1_000L);
        inputWriter.join(1_000L);
        int exitCode = finished ? process.exitValue() : -1;
        return new Result(exitCode, !finished, capture.limitExceeded(),
                capture.stdout.toString(StandardCharsets.UTF_8), capture.stderr.toString(StandardCharsets.UTF_8));
    }

    private static Thread copyAsync(InputStream input, ByteArrayOutputStream output,
            Capture capture, String name) {
        Thread reader = new Thread(() -> {
            byte[] buffer = new byte[8192];
            try (InputStream stream = input) {
                int count;
                while (!capture.limitExceeded() && (count = stream.read(buffer)) != -1) {
                    capture.append(output, buffer, count);
                }
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

    private static final class Capture {
        private final long limit;
        private final AtomicLong total = new AtomicLong();
        private final AtomicBoolean exceeded = new AtomicBoolean();
        private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        Capture(long limit) {
            this.limit = limit;
        }

        synchronized void append(ByteArrayOutputStream output, byte[] bytes, int count) {
            long remaining = limit - total.get();
            int retained = (int) Math.min(remaining, count);
            if (retained > 0) {
                output.write(bytes, 0, retained);
                total.addAndGet(retained);
            }
            if (retained < count) {
                exceeded.set(true);
            }
        }

        boolean limitExceeded() {
            return exceeded.get();
        }
    }

    record Result(int exitCode, boolean timedOut, boolean outputLimitExceeded,
            String stdout, String stderr) {
    }
}
