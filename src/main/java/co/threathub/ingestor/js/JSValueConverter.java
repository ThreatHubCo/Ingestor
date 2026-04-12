package co.threathub.ingestor.js;

import org.graalvm.polyglot.Value;

import java.time.*;
import java.util.*;

public class JSValueConverter {

    public static Object convertValue(Value v) {
        if (v == null || v.isNull()) return null;
        if (v.isBoolean()) return v.asBoolean();
        if (v.isNumber()) return convertNumber(v);
        if (v.isString()) return v.asString();
        if (v.hasArrayElements()) return convertArray(v);
        if (v.hasMembers()) return convertObject(v);
        return v.toString();
    }

    private static Object convertNumber(Value v) {
        if (v.fitsInInt()) return v.asInt();
        if (v.fitsInLong()) return v.asLong();
        if (v.fitsInDouble()) return v.asDouble();
        return v.asDouble();
    }

    private static List<Object> convertArray(Value v) {
        int size = (int) v.getArraySize();
        List<Object> list = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            list.add(convertValue(v.getArrayElement(i)));
        }
        return list;
    }

    private static Map<String, Object> convertObject(Value v) {
        Map<String, Object> map = new HashMap<>();

        for (String key : v.getMemberKeys()) {
            map.put(key, convertValue(v.getMember(key)));
        }
        return map;
    }

    public static Object convert(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime v) return v.toString();
        if (value instanceof LocalDate v) return v.toString();
        if (value instanceof LocalTime v) return v.toString();
        if (value instanceof Instant v) return v.toString();
        if (value instanceof java.sql.Timestamp v) return v.toInstant().toString();
        if (value instanceof java.sql.Date v) return v.toLocalDate().toString();
        if (value instanceof Number || value instanceof String || value instanceof Boolean) {
            return value;
        }
        return value.toString();
    }
}