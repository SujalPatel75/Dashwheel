package com.openauto.dash;

/**
 * The shell service Shizuku runs for Dashwheel as its own user (shell, or root
 * where Shizuku was started by root): ShizukuShellService answers it, ShizukuShell
 * calls it.
 */
interface IShizukuShell {
    /** Shizuku's own transaction, called by its server when the service is unbound. */
    void destroy() = 16777114;

    /** Runs [cmd] through `sh -c`; the Bundle holds "out" (both streams) and "exit" (-1 when it ran out of time). */
    Bundle run(String cmd, int timeoutS) = 1;

    /** The user the service runs as: 2000 (shell) or 0 (root). */
    int uid() = 2;
}
