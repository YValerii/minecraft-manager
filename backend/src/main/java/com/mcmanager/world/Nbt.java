package com.mcmanager.world;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A minimal NBT reader (level.dat, playerdata/*.dat, data/*.dat — gzip) that converts to JSON.
 * Large arrays (chunk data, etc.) are not expanded but replaced with a description; 64-bit numbers
 * outside the range safe for JavaScript are returned as strings.
 */
public final class Nbt {

    private static final JsonNodeFactory F = JsonNodeFactory.instance;
    private static final int MAX_ARRAY = 64;
    private static final int MAX_DEPTH = 64;
    private static final long JS_SAFE = 1L << 53;

    private Nbt() {
    }

    public static JsonNode readGzipFile(Path file) throws IOException {
        try (InputStream raw = Files.newInputStream(file);
                DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw)))) {
            byte type = in.readByte();
            if (type != 10) {
                throw new IOException("The root NBT tag must be Compound");
            }
            in.readUTF(); // root name (usually empty)
            return readPayload(in, type, 0);
        }
    }

    private static JsonNode readPayload(DataInputStream in, byte type, int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new IOException("NBT nesting too deep");
        }
        switch (type) {
            case 1:
                return F.numberNode((int) in.readByte());
            case 2:
                return F.numberNode((int) in.readShort());
            case 3:
                return F.numberNode(in.readInt());
            case 4:
                return longNode(in.readLong());
            case 5:
                return F.numberNode(in.readFloat());
            case 6:
                return F.numberNode(in.readDouble());
            case 7:
                return primitiveArray(in, 1, "byte");
            case 8:
                return F.textNode(in.readUTF());
            case 9: {
                byte elementType = in.readByte();
                int length = in.readInt();
                ArrayNode list = F.arrayNode();
                for (int i = 0; i < length; i++) {
                    JsonNode value = readPayload(in, elementType, depth + 1);
                    if (list.size() < 512) { // truncate very long lists (e.g. Inventory/Pos)
                        list.add(value);
                    }
                }
                return list;
            }
            case 10: {
                ObjectNode obj = F.objectNode();
                while (true) {
                    byte t = in.readByte();
                    if (t == 0) {
                        return obj;
                    }
                    String name = in.readUTF();
                    obj.set(name, readPayload(in, t, depth + 1));
                }
            }
            case 11:
                return primitiveArray(in, 4, "int");
            case 12:
                return primitiveArray(in, 8, "long");
            default:
                throw new IOException("Unknown NBT tag type: " + type);
        }
    }

    private static JsonNode primitiveArray(DataInputStream in, int elementSize, String name) throws IOException {
        int length = in.readInt();
        if (length > MAX_ARRAY) {
            in.skipNBytes((long) length * elementSize);
            return F.textNode(name + "[" + length + "]");
        }
        ArrayNode arr = F.arrayNode();
        for (int i = 0; i < length; i++) {
            switch (elementSize) {
                case 1 -> arr.add((int) in.readByte());
                case 4 -> arr.add(in.readInt());
                default -> arr.add(longNode(in.readLong()));
            }
        }
        return arr;
    }

    private static JsonNode longNode(long v) {
        return Math.abs(v) <= JS_SAFE ? F.numberNode(v) : F.textNode(Long.toString(v));
    }
}
