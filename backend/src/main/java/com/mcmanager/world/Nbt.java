package com.mcmanager.world;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A minimal reader and writer for gzip NBT files (level.dat, player data, data/*.dat).
 *
 * <p>{@link #readDocument} keeps every tag exactly as stored, so a file written back with
 * {@link #writeDocument} differs only in what was edited. Tags are plain Java values: Byte, Short, Integer,
 * Long, Float, Double, String, byte[], int[], long[], {@link TagList} and a Map for a compound.
 *
 * <p>{@link #readGzipFile} is the lossy view for display: large arrays are not expanded but replaced with a
 * description, and 64-bit numbers outside the range safe for JavaScript are returned as strings.
 */
public final class Nbt {

    /** A whole file. The root's name is kept, although it is usually empty. */
    public record Document(String rootName, Map<String, Object> root) {
    }

    /** A list remembers its element type, since an empty list couldn't be written back otherwise. */
    public record TagList(byte elementType, List<Object> items) {
    }

    private static final JsonNodeFactory F = JsonNodeFactory.instance;
    private static final int MAX_ARRAY = 64;
    private static final int MAX_LIST = 512;
    private static final int MAX_DEPTH = 64;
    /** Far above anything a real file holds; a corrupt length must fail rather than exhaust the heap. */
    private static final int MAX_ALLOCATION = 1 << 26;
    private static final long JS_SAFE = 1L << 53;
    private static final byte COMPOUND = 10;

    private Nbt() {
    }

    public static JsonNode readGzipFile(Path file) throws IOException {
        return toJson(readDocument(file).root());
    }

    @SuppressWarnings("unchecked")
    public static Document readDocument(Path file) throws IOException {
        try (InputStream raw = Files.newInputStream(file);
                DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw)))) {
            byte type = in.readByte();
            if (type != COMPOUND) {
                throw new IOException("The root NBT tag must be Compound");
            }
            String name = in.readUTF();
            return new Document(name, (Map<String, Object>) readPayload(in, type, 0));
        }
    }

    public static void writeDocument(Path file, Document document) throws IOException {
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(file))))) {
            out.writeByte(COMPOUND);
            out.writeUTF(document.rootName());
            writePayload(out, document.root());
        }
    }

    // ------------------------------------------------------------------ reading

    private static Object readPayload(DataInputStream in, byte type, int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new IOException("NBT nesting too deep");
        }
        switch (type) {
            case 1:
                return in.readByte();
            case 2:
                return in.readShort();
            case 3:
                return in.readInt();
            case 4:
                return in.readLong();
            case 5:
                return in.readFloat();
            case 6:
                return in.readDouble();
            case 7: {
                byte[] a = new byte[arrayLength(in)];
                in.readFully(a);
                return a;
            }
            case 8:
                return in.readUTF();
            case 9: {
                byte elementType = in.readByte();
                int length = in.readInt();
                List<Object> items = new ArrayList<>();
                for (int i = 0; i < length; i++) {
                    items.add(readPayload(in, elementType, depth + 1));
                }
                return new TagList(elementType, items);
            }
            case COMPOUND: {
                Map<String, Object> compound = new LinkedHashMap<>();
                while (true) {
                    byte t = in.readByte();
                    if (t == 0) {
                        return compound;
                    }
                    String name = in.readUTF();
                    compound.put(name, readPayload(in, t, depth + 1));
                }
            }
            case 11: {
                int[] a = new int[arrayLength(in)];
                for (int i = 0; i < a.length; i++) {
                    a[i] = in.readInt();
                }
                return a;
            }
            case 12: {
                long[] a = new long[arrayLength(in)];
                for (int i = 0; i < a.length; i++) {
                    a[i] = in.readLong();
                }
                return a;
            }
            default:
                throw new IOException("Unknown NBT tag type: " + type);
        }
    }

    private static int arrayLength(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_ALLOCATION) {
            throw new IOException("Invalid NBT array length: " + length);
        }
        return length;
    }

    // ------------------------------------------------------------------ writing

    private static byte typeOf(Object tag) {
        return switch (tag) {
            case Byte b -> 1;
            case Short s -> 2;
            case Integer i -> 3;
            case Long l -> 4;
            case Float f -> 5;
            case Double d -> 6;
            case byte[] a -> 7;
            case String s -> 8;
            case TagList l -> 9;
            case Map<?, ?> m -> COMPOUND;
            case int[] a -> 11;
            case long[] a -> 12;
            default -> throw new IllegalArgumentException("Not an NBT value: " + tag);
        };
    }

    private static void writePayload(DataOutputStream out, Object tag) throws IOException {
        switch (tag) {
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
            case TagList list -> {
                out.writeByte(list.elementType());
                out.writeInt(list.items().size());
                for (Object item : list.items()) {
                    writePayload(out, item);
                }
            }
            case Map<?, ?> compound -> {
                for (Map.Entry<?, ?> e : compound.entrySet()) {
                    out.writeByte(typeOf(e.getValue()));
                    out.writeUTF((String) e.getKey());
                    writePayload(out, e.getValue());
                }
                out.writeByte(0);
            }
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
            default -> throw new IllegalArgumentException("Not an NBT value: " + tag);
        }
    }

    // ------------------------------------------------------------------ display

    static JsonNode toJson(Object tag) {
        return switch (tag) {
            case Byte b -> F.numberNode((int) b);
            case Short s -> F.numberNode((int) s);
            case Integer i -> F.numberNode(i);
            case Long l -> longNode(l);
            case Float f -> F.numberNode(f);
            case Double d -> F.numberNode(d);
            case String s -> F.textNode(s);
            case byte[] a -> {
                if (a.length > MAX_ARRAY) {
                    yield F.textNode("byte[" + a.length + "]");
                }
                ArrayNode arr = F.arrayNode();
                for (byte x : a) {
                    arr.add((int) x);
                }
                yield arr;
            }
            case int[] a -> {
                if (a.length > MAX_ARRAY) {
                    yield F.textNode("int[" + a.length + "]");
                }
                ArrayNode arr = F.arrayNode();
                for (int x : a) {
                    arr.add(x);
                }
                yield arr;
            }
            case long[] a -> {
                if (a.length > MAX_ARRAY) {
                    yield F.textNode("long[" + a.length + "]");
                }
                ArrayNode arr = F.arrayNode();
                for (long x : a) {
                    arr.add(longNode(x));
                }
                yield arr;
            }
            case TagList list -> {
                ArrayNode arr = F.arrayNode();
                // Truncate very long lists (e.g. Inventory/Pos).
                list.items().stream().limit(MAX_LIST).forEach(item -> arr.add(toJson(item)));
                yield arr;
            }
            case Map<?, ?> compound -> {
                ObjectNode obj = F.objectNode();
                compound.forEach((k, v) -> obj.set((String) k, toJson(v)));
                yield obj;
            }
            default -> throw new IllegalArgumentException("Not an NBT value: " + tag);
        };
    }

    private static JsonNode longNode(long v) {
        return Math.abs(v) <= JS_SAFE ? F.numberNode(v) : F.textNode(Long.toString(v));
    }
}
