package com.mcmanager.world;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Writes gzip NBT files for tests from plain Java values: Byte, Short, Integer, Long, Float, Double,
 * String, byte[], int[], long[], List and Map.
 */
final class NbtWriter {

    private NbtWriter() {
    }

    static Path write(Path file, Map<String, ?> root) throws IOException {
        return writeRaw(file, 10, out -> writeCompound(out, root));
    }

    interface Body {
        void write(DataOutputStream out) throws IOException;
    }

    /** A file with the given root tag type and anything as the payload, for testing malformed files. */
    static Path writeRaw(Path file, int rootType, Body body) throws IOException {
        Files.createDirectories(file.getParent());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeByte(rootType);
            out.writeUTF("");
            body.write(out);
        }
        return Files.write(file, bytes.toByteArray());
    }

    private static void writeCompound(DataOutputStream out, Map<String, ?> values) throws IOException {
        for (Map.Entry<String, ?> e : values.entrySet()) {
            out.writeByte(typeOf(e.getValue()));
            out.writeUTF(e.getKey());
            writePayload(out, e.getValue());
        }
        out.writeByte(0);
    }

    private static int typeOf(Object v) {
        return switch (v) {
            case Byte b -> 1;
            case Short s -> 2;
            case Integer i -> 3;
            case Long l -> 4;
            case Float f -> 5;
            case Double d -> 6;
            case byte[] a -> 7;
            case String s -> 8;
            case List<?> l -> 9;
            case Map<?, ?> m -> 10;
            case int[] a -> 11;
            case long[] a -> 12;
            default -> throw new IllegalArgumentException("Not an NBT value: " + v);
        };
    }

    @SuppressWarnings("unchecked")
    private static void writePayload(DataOutputStream out, Object v) throws IOException {
        switch (v) {
            case Byte b -> out.writeByte(b);
            case Short s -> out.writeShort(s);
            case Integer i -> out.writeInt(i);
            case Long l -> out.writeLong(l);
            case Float f -> out.writeFloat(f);
            case Double d -> out.writeDouble(d);
            case byte[] a -> {
                out.writeInt(a.length);
                out.write(a);
            }
            case String s -> out.writeUTF(s);
            case List<?> list -> {
                out.writeByte(list.isEmpty() ? 0 : typeOf(list.get(0)));
                out.writeInt(list.size());
                for (Object item : list) {
                    writePayload(out, item);
                }
            }
            case Map<?, ?> m -> writeCompound(out, (Map<String, ?>) m);
            case int[] a -> {
                out.writeInt(a.length);
                for (int x : a) {
                    out.writeInt(x);
                }
            }
            case long[] a -> {
                out.writeInt(a.length);
                for (long x : a) {
                    out.writeLong(x);
                }
            }
            default -> throw new IllegalArgumentException("Not an NBT value: " + v);
        }
    }
}
