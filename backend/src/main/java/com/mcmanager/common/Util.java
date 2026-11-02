package com.mcmanager.common;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

public final class Util {

    private Util() {
    }

    public static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    /** Text value of the field, or null if it's missing / empty. */
    public static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || !v.isValueNode()) {
            return null;
        }
        String s = v.asText();
        return s.isBlank() ? null : s;
    }

    public static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> parts = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            parts.add(list.subList(i, Math.min(list.size(), i + size)));
        }
        return parts;
    }
}
