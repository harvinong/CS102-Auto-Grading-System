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

/**
 * Runs one external program (javac or a student's compiled class) and reports what happened.
 *
 * <p>In the grading flow this is the only place that starts child processes: JavaGrader and
 * TesterGrader call it once to compile a submission and once per program or tester run. Every run is bounded by a
 * wall-clock timeout and an output-size cap, so a student program that loops forever or prints
 * endlessly turns into a normal {@link Result} flag instead of hanging the whole grading batch.</p>
 *
 * <p>Arguments stay as a list so submitted paths and source text cannot be
 * reinterpreted as shell syntax.</p>
 */
final class ProcessRunner {
    // Static utility class: the private constructor stops anyone creating an instance.
    private ProcessRunner() {
    }

    /** Runs a command with no working-directory override. */
    static Result run(List<String> command, String input, Duration timeout)
            throws IOException, InterruptedException {
        // Delegate to the full overload with the inherited directory (null) and the default 1 MiB output cap.
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
        // Same as above but with a chosen working directory, e.g. the folder holding the compiled classes.
        return run(command, input, timeout, workingDirectory, 1024 * 1024L);
    }

    /**
     * Full implementation: start the process, feed it stdin, collect stdout and stderr, and kill it
     * if it exceeds the timeout or the output cap. Returns a {@link Result} describing how it ended.
     */
    static Result run(List<String> command, String input, Duration timeout,
            java.nio.file.Path workingDirectory, long outputLimitBytes)
            throws IOException, InterruptedException {
        // A zero or negative cap would make every byte "too much", so fail fast as a programming error.
        if (outputLimitBytes <= 0) {
            throw new IllegalArgumentException("output limit must be positive");
        }
        // ProcessBuilder takes the command as a list, so there is no shell and no injection through file names.
        ProcessBuilder builder = new ProcessBuilder(command);
        // Only override the directory when the caller asked for one; otherwise the child inherits ours.
        if (workingDirectory != null) {
            // Run in the requested folder so relative paths and class lookups resolve there.
            builder.directory(workingDirectory.toFile());
        }
        // Start the child; an IOException here (e.g. javac not found) propagates to the caller to be reported.
        Process process = builder.start();
        // Shared byte counter and buffers: stdout and stderr together may not exceed the one limit.
        Capture capture = new Capture(outputLimitBytes);
        // Read stdout on its own thread, otherwise a chatty program fills the OS pipe and blocks forever.
        Thread stdoutReader = copyAsync(process.getInputStream(), capture.stdout, capture, "stdout-reader");
        // Read stderr on a second thread for the same reason; javac prints its errors here.
        Thread stderrReader = copyAsync(process.getErrorStream(), capture.stderr, capture, "stderr-reader");
        // Write stdin from a separate thread too, because a program that never reads would block our write.
        Thread inputWriter = new Thread(() -> {
            try {
                // A null input means "no stdin for this run", so only write when text was supplied.
                if (input != null) {
                    // Send the test case's input as UTF-8 bytes to the student's System.in.
                    process.getOutputStream().write(input.getBytes(StandardCharsets.UTF_8));
                }
            } catch (IOException ignored) {
                // The process may have exited before consuming its input.
            } finally {
                try {
                    // Close stdin so a program waiting for end-of-input (e.g. Scanner loops) can finish.
                    process.getOutputStream().close();
                } catch (IOException ignored) {
                    // The process is already being terminated or has exited.
                }
            }
        }, "grader-input-writer");
        // Daemon thread: if the grader exits early this helper must not keep the JVM alive.
        inputWriter.setDaemon(true);
        // Begin writing the input now, while the main thread goes on to wait for the process.
        inputWriter.start();

        // Becomes true only when the process ends by itself before the deadline or the output cap.
        boolean finished;
        try {
            // Never allow a zero or negative timeout: at least 1 ms so the loop below still runs once.
            long timeoutMillis = Math.max(1L, timeout.toMillis());
            // Absolute deadline on the monotonic clock, immune to the system clock being changed.
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            // Assume the process is still running until waitFor says otherwise.
            finished = false;
            // Poll both termination and captured-byte state so timeout and output abuse stop promptly.
            while (!finished && !capture.limitExceeded()) {
                // Time left before the deadline, in nanoseconds.
                long remaining = deadline - System.nanoTime();
                // Out of time: leave the loop with finished still false, which marks this run as timed out.
                if (remaining <= 0) {
                    break;
                }
                // Wait at most 50 ms (or what remains) so the output-limit flag is re-checked frequently.
                finished = process.waitFor(Math.min(50L,
                        Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remaining))), TimeUnit.MILLISECONDS);
            }
            // Reached on timeout or on too much output: the child is still alive and must be stopped.
            if (!finished) {
                // Kill the whole process tree: a timed-out submission must not survive grading.
                terminate(process);
                // Closing stdin releases the writer thread if it was blocked on a full pipe.
                process.getOutputStream().close();
            }
        } catch (InterruptedException interrupted) {
            // Our own thread was cancelled, so still clean up the child we started.
            terminate(process);
            // Restore the interrupt flag, which catching the exception cleared, for callers up the stack.
            Thread.currentThread().interrupt();
            // Rethrow so the caller stops grading instead of treating this as a normal result.
            throw interrupted;
        }

        // Give the stdout reader up to one second to drain what the child already wrote.
        stdoutReader.join(1_000L);
        // Same bounded wait for stderr so a stuck reader cannot stall grading.
        stderrReader.join(1_000L);
        // And for the stdin writer, which should already be done once the process has stopped.
        inputWriter.join(1_000L);
        // A killed process has no meaningful exit status, so use -1 as the "did not exit normally" marker.
        int exitCode = finished ? process.exitValue() : -1;
        // Package everything for JavaGrader and TesterGrader; timedOut is the inverse of finished, and output is decoded as UTF-8.
        return new Result(exitCode, !finished, capture.limitExceeded(),
                capture.stdout.toString(StandardCharsets.UTF_8), capture.stderr.toString(StandardCharsets.UTF_8));
    }

    /** Starts a daemon thread that copies one of the child's output streams into a shared capture buffer. */
    private static Thread copyAsync(InputStream input, ByteArrayOutputStream output,
            Capture capture, String name) {
        // Wrap the copy loop in a named thread so it shows up clearly in thread dumps.
        Thread reader = new Thread(() -> {
            // Read in 8 KiB chunks, a balance between few system calls and low memory use.
            byte[] buffer = new byte[8192];
            // try-with-resources closes the stream when reading ends, releasing the pipe handle.
            try (InputStream stream = input) {
                // Number of bytes returned by the latest read; -1 means end of stream.
                int count;
                // Stop at end of stream, or as soon as the shared output cap has been hit.
                while (!capture.limitExceeded() && (count = stream.read(buffer)) != -1) {
                    // Store the chunk, truncating it if it would push the total past the limit.
                    capture.append(output, buffer, count);
                }
            } catch (IOException ignored) {
                // Process termination can close a stream while it is being read.
            }
        }, name);
        // Daemon so a reader stuck on a dead pipe never stops the application from exiting.
        reader.setDaemon(true);
        // Begin copying immediately.
        reader.start();
        // Hand the thread back so the caller can join it after the process ends.
        return reader;
    }

    /** Stops a process and everything it spawned, escalating from a polite request to a forced kill. */
    private static void terminate(Process process) throws InterruptedException {
        // First kill grandchildren (e.g. processes the student's code launched) so they cannot be orphaned.
        process.toHandle().descendants().forEach(handle -> handle.destroyForcibly());
        // Ask the main process to stop normally (SIGTERM on Unix) so it gets a chance to exit cleanly.
        process.destroy();
        // Give it 200 ms to comply; waitFor returns false if it is still alive after that.
        if (!process.waitFor(200L, TimeUnit.MILLISECONDS)) {
            // It ignored the polite request, so force-kill it (SIGKILL on Unix).
            process.destroyForcibly();
            // Wait up to one more second so the operating system has reaped it before we move on.
            process.waitFor(1_000L, TimeUnit.MILLISECONDS);
        }
        // Sweep descendants once more in case the process spawned new children while shutting down.
        process.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
    }

    /** Thread-safe store for the child's stdout and stderr that enforces one combined size limit. */
    private static final class Capture {
        // Maximum number of bytes kept across both streams.
        private final long limit;
        // Bytes retained so far from stdout and stderr combined; atomic because two reader threads add to it.
        private final AtomicLong total = new AtomicLong();
        // Set once any chunk had to be cut short, so the main loop can stop the child.
        private final AtomicBoolean exceeded = new AtomicBoolean();
        // Everything the child wrote to standard output, kept as raw bytes until decoding.
        private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        // Everything the child wrote to standard error (javac diagnostics, exception stack traces).
        private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        Capture(long limit) {
            // Remember the cap chosen by the caller of run().
            this.limit = limit;
        }

        // synchronized: stdout and stderr threads share the byte budget, so the check and update must be atomic.
        synchronized void append(ByteArrayOutputStream output, byte[] bytes, int count) {
            // How many bytes we may still keep before hitting the cap.
            long remaining = limit - total.get();
            // Keep either the whole chunk or only the part that fits.
            int retained = (int) Math.min(remaining, count);
            // Only write when there is room; a non-positive value means the buffer is already full.
            if (retained > 0) {
                // Copy the allowed prefix of the chunk into the chosen stream's buffer.
                output.write(bytes, 0, retained);
                // Update the combined total so the other stream sees the reduced budget.
                total.addAndGet(retained);
            }
            // Fewer bytes kept than received means output was dropped, i.e. the cap was exceeded.
            if (retained < count) {
                // Raise the flag; run() sees it and kills the child.
                exceeded.set(true);
            }
        }

        // True once any output has been discarded because of the size cap.
        boolean limitExceeded() {
            // Read the flag that append() sets; the run() wait loop and both reader threads poll it to stop early.
            return exceeded.get();
        }
    }

    /**
     * Outcome of one run: exit status (-1 if killed), whether the deadline passed, whether the
     * output cap was hit, and the decoded text of both streams.
     */
    record Result(int exitCode, boolean timedOut, boolean outputLimitExceeded,
            String stdout, String stderr) {
    }
}
