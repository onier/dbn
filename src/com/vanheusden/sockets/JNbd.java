package com.vanheusden.sockets;

/* Released under GPL 2.0
 * (C) 2009 by folkert@vanheusden.com
 */

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Kernel32Util;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;

import java.nio.ByteBuffer;
import java.util.concurrent.Semaphore;

class JNbd {

    static String version = "JNbd v0.4, (C) 2009 by folkert@vanheusden.com";
    static Semaphore statisticsSemaphore = new Semaphore(1);
    static double runningSince;
    static long totalBytesWritten;
    static long totalBytesRead;
    static double totalSessionsLength;
    static int nSessions;
    static double totalIdleTime;
    static double totalProcessingTime;
    static int totalNCommands, totalNRead, totalNWrite;
    static int nExceptions;

    private static boolean debug = false;
    private MyServerSocket myServerSocket;

    static public void showException(Exception e) {
        System.err.println("Exception: " + e);
        System.err.println("Details: " + e.getMessage());
        System.err.println("Stack-trace:");
        for (StackTraceElement ste : e.getStackTrace()) {
            System.err.println(" " + ste.getClassName() + ", "
                    + ste.getFileName() + ", "
                    + ste.getLineNumber() + ", "
                    + ste.getMethodName() + ", "
                    + (ste.isNativeMethod()
                            ? "is native method" : "NOT a native method"));
        }
    }

    void initSession(long storageSize) throws Exception {
        if (debug) {
            System.err.println("Init session");
        }

        if (debug) {
            System.err.println("\"password\"");
        }
        int passwordMsg[] = {'N', 'B', 'D', 'M', 'A', 'G', 'I', 'C'};
        myServerSocket.putBytes(passwordMsg);
        myServerSocket.flush();

        if (debug) {
            System.err.println("\"magic\"");
        }
        int magicMsg[] = {0x00, 0x00, 0x42, 0x02, 0x81, 0x86, 0x12, 0x53};
        myServerSocket.putBytes(magicMsg);
        myServerSocket.flush();

        if (debug) {
            System.err.println("\"storage size\"");
        }
        int storageSizeMsg[] = {
            (int) ((storageSize >> 56) & 255),
            (int) ((storageSize >> 48) & 255),
            (int) ((storageSize >> 40) & 255),
            (int) ((storageSize >> 32) & 255),
            (int) ((storageSize >> 24) & 255),
            (int) ((storageSize >> 16) & 255),
            (int) ((storageSize >> 8) & 255),
            (int) ((storageSize) & 255)};
        myServerSocket.putBytes(storageSizeMsg);
        myServerSocket.flush();

        if (debug) {
            System.err.println("\"padding\"");
        }
        int[] padMsg = new int[128];
        myServerSocket.putBytes(padMsg);
        myServerSocket.flush();
    }

    private void msgWrite(WinNT.HANDLE fileHandle, long handle, long offset, long len) throws Exception {
        if (debug) {
            System.err.println("write " + offset + " " + len);
        }
        Kernel32.INSTANCE.SetFilePointerEx(fileHandle, offset, Pointer.NULL, 0);
        while (len > 0) {
            int currentLength = (int) Math.min(len, 65536);
            byte[] buffer = new byte[currentLength];
            IntByReference noUsed = new IntByReference();
            myServerSocket.getBytes(buffer);
            Kernel32.INSTANCE.WriteFile(fileHandle, buffer, currentLength, noUsed, null);
            offset = offset + noUsed.getValue();
            Kernel32.INSTANCE.SetFilePointerEx(fileHandle, offset, Pointer.NULL, 0);
//            fileHandle.write(buffer);
            len -= currentLength;
        }

        myServerSocket.putU32(0x67446698);
        myServerSocket.putU32(0);
        myServerSocket.putU64(handle);
        myServerSocket.flush();
    }

    private void msgRead(WinNT.HANDLE fileHandle, long handle, long offset, long len) throws Exception {
        if (debug) {
            System.err.println("read " + offset + " " + len);
        }
        Kernel32.INSTANCE.SetFilePointerEx(fileHandle, offset, Pointer.NULL, 0);
        myServerSocket.putU32(0x67446698);
        myServerSocket.putU32(0);
        myServerSocket.putU64(handle);

        while (len > 0) {
            int currentLength = (int) Math.min(len, 65536);
            ByteBuffer buffer = ByteBuffer.allocate(currentLength);
            IntByReference noUsed = new IntByReference();
            if (!Kernel32.INSTANCE.ReadFile(fileHandle, buffer, currentLength, noUsed, null)) {
                int code = Kernel32.INSTANCE.GetLastError();
                System.out.println(Kernel32Util.formatMessage(code));
            } else {
                if (noUsed.getValue() < currentLength) {
                    throw new Exception("short read ");
                }
            }
            offset = offset + noUsed.getValue();
            Kernel32.INSTANCE.SetFilePointerEx(fileHandle, offset, Pointer.NULL, 0);
            byte[] bs = new byte[buffer.limit()];
            buffer.get(bs);
            myServerSocket.putBytes(bs);

            len -= currentLength;
        }

        myServerSocket.flush();
    }

    JNbd(String adapter, int port, String devName) throws Exception {
//        long fileSize = new File(devName.substring(devName.length() - 2, devName.length())).getTotalSpace();
        WinNT.HANDLE hCD = Kernel32.INSTANCE.CreateFile(devName, WinNT.GENERIC_WRITE | WinNT.GENERIC_READ, WinNT.FILE_SHARE_READ | WinNT.FILE_SHARE_WRITE, null, WinNT.OPEN_EXISTING, 0, null);
        umount(hCD);
        Memory memory = new Memory(8);
        IntByReference ibr = new IntByReference();
        Kernel32.INSTANCE.DeviceIoControl(hCD, 0x7405c, Pointer.NULL, 0, memory, 8, ibr, Pointer.NULL);
        long fileSize = (memory.getLong(0));
        myServerSocket = new MyServerSocket(adapter, port);

        for (;;) {
            long sessionStartTs = -1, idleStartTs = 0, idleEndTs = 0;

            try {
                idleStartTs = System.currentTimeMillis();
                myServerSocket.acceptConnection();
                idleEndTs = System.currentTimeMillis();
                sessionStartTs = System.currentTimeMillis();

                initSession(fileSize);

                for (;;) {
                    long offset;

                    // System.err.println("get magic");
                    int magic = myServerSocket.getU32();
                    long commandStartTs = System.currentTimeMillis();
                    if (magic != 0x25609513) {
                        throw new Exception("Invalid magic " + magic);
                    }
                    int type = myServerSocket.getU32();
                    long handle = myServerSocket.getU64();
                    long hp = myServerSocket.getU32();
                    long lp = myServerSocket.getU32();
                    long len = myServerSocket.getU32();

                    if (lp < 0) {
                        lp += (long) 1 << 32;
                    }
                    if (hp < 0) {
                        hp += (long) 1 << 32;
                    }
                    if (len < 0) {
                        len += (long) 1 << 32;
                    }

                    offset = (hp << 32) + lp;

                    if (type == 1) // WRITE
                    {
                        msgWrite(hCD, handle, offset, len);
                        statisticsSemaphore.acquire();
                        totalBytesWritten += len;
                        totalNWrite++;
                        statisticsSemaphore.release();
                    } else if (type == 0) // READ
                    {
                        msgRead(hCD, handle, offset, len);
                        statisticsSemaphore.acquire();
                        totalBytesRead += len;
                        totalNRead++;
                        statisticsSemaphore.release();
                    } else if (type == 2) // DISCONNECT
                    {
                        if (debug) {
                            System.out.println("End of session");
                        }

                        break;
                    } else {
                        throw new Exception("Unknown message type: " + type);
                    }

                    long commandEndTs = System.currentTimeMillis();

                    statisticsSemaphore.acquire();
                    totalProcessingTime += ((double) commandEndTs - commandStartTs) / 1000.0;
                    totalNCommands++;
                    statisticsSemaphore.release();
                }
            } catch (Exception e) {
                showException(e);
                nExceptions++;
            } finally {
                myServerSocket.closeSocket();
            }

            long sessionEndTs = System.currentTimeMillis();

            statisticsSemaphore.acquire();
            if (sessionStartTs != -1) {
                totalSessionsLength += (double) (sessionEndTs - sessionStartTs) / 1000.0;
                nSessions++;
                totalIdleTime += (double) (idleEndTs - idleStartTs) / 1000.0;
            }
            statisticsSemaphore.release();
        }
    }

    public static void help() {
        System.out.println("--adapter x    adapter to listen on, default is all");
        System.out.println("--port x       port to listen on, default is 12345");
        System.out.println("--file x       file to server");
        System.out.println("--http-listen-port x   port on which the web-server will listen (for statistics)");
        System.out.println("--debug        enable debug mode");
        System.out.println("--help         this help");
    }

    public static void main(String[] args) throws Exception {
        String adapter = "0.0.0.0", httpListenAdapter = "0.0.0.0";
        String devName = "\\\\.\\G:";
        JNbd jnbd = new JNbd(adapter, 1234, devName);
    }
}
