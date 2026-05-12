package com.bro.brorcc.utils;

import android.content.Context;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Diagnostics logging utility that keeps an in-memory ring buffer and writes
 * logs to rotating files on disk.
 */
public class DiagLog {
    private static final String TAG = "BroRCC";
    private static final int MAX_ENTRIES = 200;
    private static final long MAX_FILE_SIZE = 1_000_000L; // 1MB
    private static final int MAX_FILES = 3;
    private static final String FILE_PREFIX = "diaglog";

    private static final Deque<String> buffer = new ArrayDeque<>(MAX_ENTRIES);
    private static File logDir;
    private static File currentFile;
    private static long lastHeartbeat = 0L;

    private static final SimpleDateFormat TS_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    private DiagLog() { }

    /** Initialize logging with application context. */
    public static synchronized void init(Context ctx) {
        if (logDir != null) return;
        logDir = new File(ctx.getFilesDir(), "logs");
        if (!logDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            logDir.mkdirs();
        }
        currentFile = new File(logDir, FILE_PREFIX + "0.log");
    }

    private static synchronized void append(String level, String msg, Throwable t) {
        String ts = TS_FORMAT.format(new Date());
        String entry = ts + " " + level + "/" + TAG + ": " + msg;
        if (t != null) {
            entry += "\n" + Log.getStackTraceString(t);
        }

        if (buffer.size() >= MAX_ENTRIES) {
            buffer.removeFirst();
        }
        buffer.addLast(entry);

        if (logDir != null) {
            try {
                if (currentFile == null) {
                    currentFile = new File(logDir, FILE_PREFIX + "0.log");
                }
                try (FileWriter fw = new FileWriter(currentFile, true);
                     BufferedWriter bw = new BufferedWriter(fw)) {
                    bw.write(entry);
                    bw.newLine();
                }
                if (currentFile.length() >= MAX_FILE_SIZE) {
                    rotateFiles();
                }
            } catch (IOException ignore) {
                // Ignore logging failures
            }
        }
    }

    private static void rotateFiles() {
        for (int i = MAX_FILES - 1; i > 0; i--) {
            File dst = new File(logDir, FILE_PREFIX + i + ".log");
            File src = new File(logDir, FILE_PREFIX + (i - 1) + ".log");
            if (dst.exists()) {
                //noinspection ResultOfMethodCallIgnored
                dst.delete();
            }
            if (src.exists()) {
                //noinspection ResultOfMethodCallIgnored
                src.renameTo(dst);
            }
        }
        currentFile = new File(logDir, FILE_PREFIX + "0.log");
        if (currentFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            currentFile.delete();
        }
    }
    private static String callerTag() {
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        for (StackTraceElement e : st) {
            String cls = e.getClassName();
            if (!cls.equals(DiagLog.class.getName()) && !cls.startsWith("java.lang.Thread")) {
                int dot = cls.lastIndexOf('.');
                return dot >= 0 ? cls.substring(dot + 1) : cls;
            }
        }
        return TAG; // Fallback
    }

    public static void d(String msg) {
        Log.d(TAG, msg);
        append("D", msg, null);
    }

    public static void i(String msg) {
        String tag = callerTag();
        Log.i(tag, msg);
        append("I", "[" + tag + "] " + msg, null);
    }

    public static void e(String msg) {  // Overload
        String tag = callerTag();
        Log.e(tag, msg);
        append("E", "[" + tag + "] " + msg, null);
    }

    public static void e(String msg, Throwable t) {
        String tag = callerTag();
        Log.e(tag, msg, t);
        append("E", "[" + tag + "] " + msg, t);
    }
    public static void w(String msg) {
        Log.w(TAG, msg);
        append("W", msg, null);
    }


    /** Return up to {@code n} most recent log lines. */
    public static synchronized List<String> getRecentLogs(int n) {
        int size = Math.min(n, buffer.size());
        List<String> list = new ArrayList<>(size);
        int start = buffer.size() - size;
        int idx = 0;
        for (String line : buffer) {
            if (idx++ >= start) {
                list.add(line);
            }
        }
        return list;
    }

    /** Record a heartbeat event timestamp. */
    public static synchronized void heartbeat() {
        lastHeartbeat = System.currentTimeMillis();
    }

    /** Return timestamp of last heartbeat, or 0 if none. */
    public static synchronized long getLastHeartbeat() {
        return lastHeartbeat;
    }
}

