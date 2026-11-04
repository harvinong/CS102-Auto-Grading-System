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
        // Return this computed result to the caller.
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
        // Return this computed result to the caller.
        return run(command, input, timeout, workingDirectory, 1024 * 1024L);
    }

    static Result run(List<String> command, String input, Duration timeout,
            java.nio.file.Path workingDirectory, long outputLimitBytes)
            throws IOException, InterruptedException {
        // Check this condition so grading follows the intended path.
        if (outputLimitBytes <= 0) {
            // Report this unrecoverable condition to the caller.
            throw new IllegalArgumentException("output limit must be positive");
        }
        // Prepare or update the value needed by the next grading step.
        ProcessBuilder builder = new ProcessBuilder(command);
        // Check this condition so grading follows the intended path.
        if (workingDirectory != null) {
            // Perform this small step of the grading workflow.
            builder.directory(workingDirectory.toFile());
        }
        // Prepare or update the value needed by the next grading step.
        Process process = builder.start();
        // Prepare or update the value needed by the next grading step.
        Capture capture = new Capture(outputLimitBytes);
        // Prepare or update the value needed by the next grading step.
        Thread stdoutReader = copyAsync(process.getInputStream(), capture.stdout, capture, "stdout-reader");
        // Prepare or update the value needed by the next grading step.
        Thread stderrReader = copyAsync(process.getErrorStream(), capture.stderr, capture, "stderr-reader");
        Thread inputWriter = new Thread(() -> {
            // Perform this small step of the grading workflow.
            try {
                // Check this condition so grading follows the intended path.
                if (input != null) {
                    // Perform this small step of the grading workflow.
                    process.getOutputStream().write(input.getBytes(StandardCharsets.UTF_8));
                }
            } catch (IOException ignored) {
                // The process may have exited before consuming its input.
            } finally {
                // Perform this small step of the grading workflow.
                try {
                    // Perform this small step of the grading workflow.
                    process.getOutputStream().close();
                } catch (IOException ignored) {
                    // The process is already being terminated or has exited.
                }
            }
        // Perform this small step of the grading workflow.
        }, "grader-input-writer");
        // Perform this small step of the grading workflow.
        inputWriter.setDaemon(true);
        // Perform this small step of the grading workflow.
        inputWriter.start();

        // Perform this small step of the grading workflow.
        boolean finished;
        // Perform this small step of the grading workflow.
        try {
            // Prepare or update the value needed by the next grading step.
            long timeoutMillis = Math.max(1L, timeout.toMillis());
            // Prepare or update the value needed by the next grading step.
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            // Prepare or update the value needed by the next grading step.
            finished = false;
            // Repeat this operation for each relevant grading item.
            while (!finished && !capture.limitExceeded()) {
                // Prepare or update the value needed by the next grading step.
                long remaining = deadline - System.nanoTime();
                // Check this condition so grading follows the intended path.
                if (remaining <= 0) {
                    // Perform this small step of the grading workflow.
                    break;
                }
                finished = process.waitFor(Math.min(50L,
                        // Perform this small step of the grading workflow.
                        Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remaining))), TimeUnit.MILLISECONDS);
            }
            // Check this condition so grading follows the intended path.
            if (!finished) {
                // Kill the whole process tree: a timed-out submission must not survive grading.
                terminate(process);
                // Perform this small step of the grading workflow.
                process.getOutputStream().close();
            }
        } catch (InterruptedException interrupted) {
            // Preserve cancellation while still cleaning up the child we started.
            terminate(process);
            // Perform this small step of the grading workflow.
            Thread.currentThread().interrupt();
            // Report this unrecoverable condition to the caller.
            throw interrupted;
        }

        // Perform this small step of the grading workflow.
        stdoutReader.join(1_000L);
        // Perform this small step of the grading workflow.
        stderrReader.join(1_000L);
        // Perform this small step of the grading workflow.
        inputWriter.join(1_000L);
        // Prepare or update the value needed by the next grading step.
        int exitCode = finished ? process.exitValue() : -1;
        // Return this computed result to the caller.
        return new Result(exitCode, !finished, capture.limitExceeded(),
                // Perform this small step of the grading workflow.
                capture.stdout.toString(StandardCharsets.UTF_8), capture.stderr.toString(StandardCharsets.UTF_8));
    }

    private static Thread copyAsync(InputStream input, ByteArrayOutputStream output,
            Capture capture, String name) {
        Thread reader = new Thread(() -> {
            // Prepare or update the value needed by the next grading step.
            byte[] buffer = new byte[8192];
            // Prepare or update the value needed by the next grading step.
            try (InputStream stream = input) {
                // Perform this small step of the grading workflow.
                int count;
                // Repeat this operation for each relevant grading item.
                while (!capture.limitExceeded() && (count = stream.read(buffer)) != -1) {
                    // Perform this small step of the grading workflow.
                    capture.append(output, buffer, count);
                }
            } catch (IOException ignored) {
                // Process termination can close a stream while it is being read.
            }
        // Perform this small step of the grading workflow.
        }, name);
        // Perform this small step of the grading workflow.
        reader.setDaemon(true);
        // Perform this small step of the grading workflow.
        reader.start();
        // Return this computed result to the caller.
        return reader;
    }

    private static void terminate(Process process) throws InterruptedException {
        // Perform this small step of the grading workflow.
        process.toHandle().descendants().forEach(handle -> handle.destroyForcibly());
        // Perform this small step of the grading workflow.
        process.destroy();
        // Check this condition so grading follows the intended path.
        if (!process.waitFor(200L, TimeUnit.MILLISECONDS)) {
            // Perform this small step of the grading workflow.
            process.destroyForcibly();
            // Perform this small step of the grading workflow.
            process.waitFor(1_000L, TimeUnit.MILLISECONDS);
        }
        // Perform this small step of the grading workflow.
        process.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
    }

    private static final class Capture {
        // Perform this small step of the grading workflow.
        private final long limit;
        // Prepare or update the value needed by the next grading step.
        private final AtomicLong total = new AtomicLong();
        // Prepare or update the value needed by the next grading step.
        private final AtomicBoolean exceeded = new AtomicBoolean();
        // Prepare or update the value needed by the next grading step.
        private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        // Prepare or update the value needed by the next grading step.
        private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        Capture(long limit) {
            // Prepare or update the value needed by the next grading step.
            this.limit = limit;
        }

        synchronized void append(ByteArrayOutputStream output, byte[] bytes, int count) {
            // Prepare or update the value needed by the next grading step.
            long remaining = limit - total.get();
            // Prepare or update the value needed by the next grading step.
            int retained = (int) Math.min(remaining, count);
            // Check this condition so grading follows the intended path.
            if (retained > 0) {
                // Perform this small step of the grading workflow.
                output.write(bytes, 0, retained);
                // Perform this small step of the grading workflow.
                total.addAndGet(retained);
            }
            // Check this condition so grading follows the intended path.
            if (retained < count) {
                // Perform this small step of the grading workflow.
                exceeded.set(true);
            }
        }

        boolean limitExceeded() {
            // Return this computed result to the caller.
            return exceeded.get();
        }
    }

    record Result(int exitCode, boolean timedOut, boolean outputLimitExceeded,
            String stdout, String stderr) {
    }
}
