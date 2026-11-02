package com.mcmanager.common;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** SHA-1 / SHA-512 for Modrinth and the murmur2 fingerprint for CurseForge. */
public final class Hashing {

    private Hashing() {
    }

    public static String sha1(Path file) throws IOException {
        return digest(file, "SHA-1");
    }

    public static String digest(Path file, String algorithm) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
            return hex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /**
     * CurseForge fingerprint: murmur2 (seed = 1) over the file contents
     * with whitespace, tabs, and line breaks stripped out (bytes 9, 10, 13, 32).
     */
    public static long curseForgeFingerprint(Path file) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.min(Files.size(file), Integer.MAX_VALUE - 8));
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                for (int i = 0; i < n; i++) {
                    byte b = buf[i];
                    if (b != 9 && b != 10 && b != 13 && b != 32) {
                        out.write(b);
                    }
                }
            }
        }
        return murmur2(out.toByteArray(), 1);
    }

    static long murmur2(byte[] data, int seed) {
        final int m = 0x5bd1e995;
        final int r = 24;
        int length = data.length;
        int h = seed ^ length;
        int len4 = length >> 2;
        for (int i = 0; i < len4; i++) {
            int i4 = i << 2;
            int k = (data[i4] & 0xff)
                    | ((data[i4 + 1] & 0xff) << 8)
                    | ((data[i4 + 2] & 0xff) << 16)
                    | ((data[i4 + 3] & 0xff) << 24);
            k *= m;
            k ^= k >>> r;
            k *= m;
            h *= m;
            h ^= k;
        }
        int offset = len4 << 2;
        switch (length & 3) {
            case 3:
                h ^= (data[offset + 2] & 0xff) << 16;
            case 2:
                h ^= (data[offset + 1] & 0xff) << 8;
            case 1:
                h ^= data[offset] & 0xff;
                h *= m;
            default:
                break;
        }
        h ^= h >>> 13;
        h *= m;
        h ^= h >>> 15;
        return h & 0xFFFFFFFFL;
    }
}
