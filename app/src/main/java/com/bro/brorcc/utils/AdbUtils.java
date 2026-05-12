package com.bro.brorcc.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/** Utility methods for ensuring the ADB daemon is listening on TCP. */
public class AdbUtils {
    private AdbUtils() { }

    /** Returns true if the ADB daemon is listening on TCP port 5555. */
    public static boolean isAdbTcpEnabled() {
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(new String[]{"getprop", "service.adb.tcp.port"});
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String v = r.readLine();
                p.waitFor();
                return "5555".equals(v != null ? v.trim() : null);
            }
        } catch (Exception e) {
            DiagLog.e("Error checking ADB TCP state", e);
            return false;
        } finally {
            if (p != null) {
                p.destroy();
            }
        }
    }

    /** Ensures the ADB daemon listens on TCP port 5555, restarting it if necessary. */
    public static void ensureAdbTcpEnabled() {
        if (isAdbTcpEnabled()) return;
        CommandResult setProp = ShellCommandRunner.runAsRoot("setprop service.adb.tcp.port 5555");
        if (setProp == null || setProp.exitCode != 0) {
            DiagLog.e("Failed to set service.adb.tcp.port");
            return;
        }
        CommandResult start = ShellCommandRunner.runAsRoot("start adbd");
        if (start == null || start.exitCode != 0) {
            DiagLog.e("Failed to start adbd");
        }
    }
}
