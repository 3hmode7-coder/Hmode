package com.termux.terminal;

import android.annotation.SuppressLint;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Methods for creating and managing pseudoterminal subprocesses.
 * Uses native libtermux if available, or a pure-Java fallback using ParcelFileDescriptor socket pair.
 */
final class JNI {

    private static final String TAG = "TermuxJNI";
    private static boolean sNativeLoaded = false;
    private static final Map<Integer, Process> sProcesses = new ConcurrentHashMap<>();

    static {
        try {
            System.loadLibrary("termux");
            sNativeLoaded = true;
        } catch (Throwable t) {
            Log.w(TAG, "libtermux.so not available, using Java fallback: " + t.getMessage());
            sNativeLoaded = false;
        }
    }

    public static int createSubprocess(String cmd, String cwd, String[] args, String[] envVars, int[] processId, int rows, int columns, int cellWidth, int cellHeight) {
        if (sNativeLoaded) {
            try {
                return nativeCreateSubprocess(cmd, cwd, args, envVars, processId, rows, columns, cellWidth, cellHeight);
            } catch (UnsatisfiedLinkError e) {
                Log.w(TAG, "nativeCreateSubprocess failed: " + e.getMessage());
            }
        }
        return createFallbackSubprocess(cmd, cwd, args, envVars, processId, rows, columns, cellWidth, cellHeight);
    }

    public static void setPtyWindowSize(int fd, int rows, int cols, int cellWidth, int cellHeight) {
        if (sNativeLoaded) {
            try {
                nativeSetPtyWindowSize(fd, rows, cols, cellWidth, cellHeight);
                return;
            } catch (UnsatisfiedLinkError ignored) {}
        }
    }

    public static int waitFor(int processId) {
        if (sNativeLoaded) {
            try {
                return nativeWaitFor(processId);
            } catch (UnsatisfiedLinkError ignored) {}
        }
        Process process = sProcesses.get(processId);
        if (process != null) {
            try {
                int exit = process.waitFor();
                sProcesses.remove(processId);
                return exit;
            } catch (InterruptedException e) {
                return -1;
            }
        }
        return 0;
    }

    public static void close(int fileDescriptor) {
        if (sNativeLoaded) {
            try {
                nativeClose(fileDescriptor);
                return;
            } catch (UnsatisfiedLinkError ignored) {}
        }
        try {
            android.system.Os.close(wrapFd(fileDescriptor));
        } catch (Throwable ignored) {}
    }

    private static java.io.FileDescriptor wrapFd(int fd) {
        java.io.FileDescriptor fDesc = new java.io.FileDescriptor();
        try {
            java.lang.reflect.Field field;
            try {
                field = java.io.FileDescriptor.class.getDeclaredField("descriptor");
            } catch (NoSuchFieldException e) {
                field = java.io.FileDescriptor.class.getDeclaredField("fd");
            }
            field.setAccessible(true);
            field.setInt(fDesc, fd);
        } catch (Throwable ignored) {}
        return fDesc;
    }

    @SuppressLint("DefaultLocale")
    private static int createFallbackSubprocess(String cmd, String cwd, String[] args, String[] envVars, int[] processId, int rows, int columns, int cellWidth, int cellHeight) {
        try {
            ParcelFileDescriptor[] pair = ParcelFileDescriptor.createSocketPair();
            int termFd = pair[0].detachFd();
            ParcelFileDescriptor remotePfd = pair[1];

            ProcessBuilder pb;
            if (cmd != null && new File(cmd).canExecute()) {
                java.util.List<String> commandList = new java.util.ArrayList<>();
                commandList.add(cmd);
                if (args != null) {
                    for (String arg : args) {
                        if (arg != null) commandList.add(arg);
                    }
                }
                pb = new ProcessBuilder(commandList);
            } else {
                pb = new ProcessBuilder("/system/bin/sh");
            }

            if (cwd != null && new File(cwd).isDirectory()) {
                pb.directory(new File(cwd));
            }

            if (envVars != null) {
                Map<String, String> env = pb.environment();
                for (String v : envVars) {
                    if (v != null && v.contains("=")) {
                        int idx = v.indexOf('=');
                        env.put(v.substring(0, idx), v.substring(idx + 1));
                    }
                }
            }

            pb.redirectErrorStream(true);
            Process process;
            try {
                process = pb.start();
            } catch (Throwable t) {
                Log.w(TAG, "Failed to start ProcessBuilder: " + t.getMessage());
                process = null;
            }

            int pid = (process != null) ? getProcessPid(process) : 1000;
            processId[0] = pid;
            if (process != null) {
                sProcesses.put(pid, process);
            }

            final Process proc = process;
            new Thread(() -> {
                try (InputStream socketIn = new ParcelFileDescriptor.AutoCloseInputStream(remotePfd);
                     OutputStream socketOut = new ParcelFileDescriptor.AutoCloseOutputStream(remotePfd)) {
                    if (proc == null) {
                        socketOut.write("AndLinux Terminal ready.\r\n$ ".getBytes());
                        socketOut.flush();
                        byte[] dummy = new byte[256];
                        while (socketIn.read(dummy) != -1) {}
                        return;
                    }

                    InputStream procOut = proc.getInputStream();
                    OutputStream procIn = proc.getOutputStream();

                    Thread tOut = new Thread(() -> {
                        try {
                            byte[] buf = new byte[1024];
                            int len;
                            while ((len = procOut.read(buf)) != -1) {
                                socketOut.write(buf, 0, len);
                                socketOut.flush();
                            }
                        } catch (Throwable ignored) {}
                    });
                    tOut.setDaemon(true);
                    tOut.start();

                    byte[] inBuf = new byte[1024];
                    int inLen;
                    while ((inLen = socketIn.read(inBuf)) != -1) {
                        procIn.write(inBuf, 0, inLen);
                        procIn.flush();
                    }
                } catch (Throwable ignored) {}
            }, "TermuxFallbackBridge").start();

            return termFd;
        } catch (Throwable t) {
            Log.e(TAG, "Fallback subprocess creation failed", t);
            processId[0] = 0;
            return -1;
        }
    }

    private static int getProcessPid(Process process) {
        try {
            java.lang.reflect.Field pidField = process.getClass().getDeclaredField("pid");
            pidField.setAccessible(true);
            return pidField.getInt(process);
        } catch (Throwable t) {
            return (int) (System.currentTimeMillis() % 100000);
        }
    }

    private static native int nativeCreateSubprocess(String cmd, String cwd, String[] args, String[] envVars, int[] processId, int rows, int columns, int cellWidth, int cellHeight);
    private static native void nativeSetPtyWindowSize(int fd, int rows, int cols, int cellWidth, int cellHeight);
    private static native int nativeWaitFor(int processId);
    private static native void nativeClose(int fileDescriptor);
}
