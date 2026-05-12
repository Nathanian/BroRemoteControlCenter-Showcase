package com.bro.brorcc.utils;

/** Result of executing a shell command. */
public class CommandResult {
    public final String stdout;
    public final String stderr;
    public final int exitCode;

    public CommandResult(String stdout, String stderr, int exitCode) {
        this.stdout = stdout;
        this.stderr = stderr;
        this.exitCode = exitCode;
    }
}
