package com.bro.brorcc.utils;

import android.os.Build;

import java.io.*;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Utility to execute shell commands with timeouts. Android-7 compatible. */
public class ShellCommandRunner {
    private static final long DEFAULT_TIMEOUT_MS = 5_000L;

    private ShellCommandRunner() { }

    public static CommandResult run(String command) {
        return run(command, DEFAULT_TIMEOUT_MS);
    }

    public static CommandResult run(String[] command) {
        return run(command, DEFAULT_TIMEOUT_MS);
    }

    /** Executes the given command through {@code su -c}. */
    public static CommandResult runAsRoot(String command) {
        return runAsRoot(command, DEFAULT_TIMEOUT_MS);
    }

    /** Executes the given command through {@code su -c} with a timeout. */
    public static CommandResult runAsRoot(String command, long timeoutMs) {
        return run(new String[]{"su", "-c", command}, timeoutMs);
    }

    public static CommandResult run(String command, long timeoutMs) {
        return runInternal(command, null, timeoutMs);
    }

    public static CommandResult run(String[] command, long timeoutMs) {
        return runInternal(null, command, timeoutMs);
    }

    private static CommandResult runInternal(String cmd, String[] cmdArray, long timeoutMs) {
        Process p = null;
        StringBuilder outBuf = new StringBuilder();
        StringBuilder errBuf = new StringBuilder();
        Thread outDrainer = null;
        Thread errDrainer = null;

        try {
            p = (cmdArray != null)
                    ? Runtime.getRuntime().exec(cmdArray)
                    : Runtime.getRuntime().exec(cmd);

            // Drain stdout/stderr in parallel to avoid blocking on full pipes
            final BufferedInputStream stdout = new BufferedInputStream(p.getInputStream());
            final BufferedInputStream stderr = new BufferedInputStream(p.getErrorStream());

            outDrainer = new Thread(() -> readAll(stdout, outBuf), "proc-stdout");
            errDrainer = new Thread(() -> readAll(stderr, errBuf), "proc-stderr");
            outDrainer.setDaemon(true);
            errDrainer.setDaemon(true);
            outDrainer.start();
            errDrainer.start();

            boolean finished;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // API 26+: native timed wait
                finished = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            } else {
                // API 24/25: compat timed wait
                finished = waitForWithTimeoutCompat(p, timeoutMs, TimeUnit.MILLISECONDS);
            }

            if (!finished) {
                DiagLog.w("Command timeout: " + toCmdString(cmd, cmdArray));
                safeDestroy(p);
                // best effort join drains
                joinQuietly(outDrainer, 200);
                joinQuietly(errDrainer, 200);
                return new CommandResult(outBuf.toString(), errBuf.toString(), -1);
            }

            int exit = p.exitValue();

            // Let drainer threads finish (short join, they usually end right after process exit)
            joinQuietly(outDrainer, 200);
            joinQuietly(errDrainer, 200);

            if (exit != 0) {
                String errOut = errBuf.toString();
                DiagLog.w("Command failed (" + exit + "): " + toCmdString(cmd, cmdArray)
                        + (errOut.isEmpty() ? "" : ": " + errOut.trim()));
            }
            return new CommandResult(outBuf.toString(), errBuf.toString(), exit);

        } catch (Throwable e) {
            DiagLog.e("Error executing command: " + toCmdString(cmd, cmdArray), e);
            return null;
        } finally {
            if (p != null) {
                try { p.getInputStream().close(); } catch (IOException ignored) {}
                try { p.getErrorStream().close(); } catch (IOException ignored) {}
                try { p.getOutputStream().close(); } catch (IOException ignored) {}
                try { p.destroy(); } catch (Throwable ignored) {}
            }
        }
    }

    // ------- helpers -------

    private static boolean waitForWithTimeoutCompat(Process p, long timeout, TimeUnit unit) {
        final CountDownLatch done = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            try { p.waitFor(); } catch (InterruptedException ignored) {}
            finally { done.countDown(); }
        }, "proc-wait");
        t.setDaemon(true);
        t.start();
        try {
            return done.await(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void readAll(InputStream in, StringBuilder sink) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = br.readLine()) != null) {
                synchronized (sink) {
                    if (sink.length() > 0) sink.append('\n');
                    sink.append(line);
                }
            }
        } catch (IOException ignored) { }
    }

    private static void joinQuietly(Thread t, long millis) {
        if (t == null) return;
        try { t.join(millis); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }

    private static void safeDestroy(Process p) {
        try { p.destroy(); } catch (Throwable ignored) {}
        try { Thread.sleep(150); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        try { p.destroy(); } catch (Throwable ignored) {}
        // destroyForcibly isn't available on very old Androids; keep it defensive:
        try { p.getClass().getMethod("destroyForcibly").invoke(p); } catch (Throwable ignored) {}
    }

    private static String toCmdString(String cmd, String[] cmdArray) {
        return (cmdArray != null) ? Arrays.toString(cmdArray) : cmd;
    }
}
