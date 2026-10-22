package com.astraai.ui;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small strict JSON parser for the fixed, versioned KDF report schema. */
final class StrictJsonParser {
    private final String input;
    private int index;

    private StrictJsonParser(String input) {
        this.input = input;
    }

    static Object parse(String input) {
        if (input == null) {
            throw new IllegalArgumentException("report JSON is required");
        }
        StrictJsonParser parser = new StrictJsonParser(input);
        Object value = parser.readValue(0);
        parser.skipWhitespace();
        if (parser.index != input.length()) {
            throw parser.invalid();
        }
        return value;
    }

    private Object readValue(int depth) {
        if (depth > 24) {
            throw invalid();
        }
        skipWhitespace();
        if (index >= input.length()) {
            throw invalid();
        }
        return switch (input.charAt(index)) {
            case '{' -> readObject(depth + 1);
            case '[' -> readArray(depth + 1);
            case '"' -> readString();
            case 't' -> readLiteral("true", Boolean.TRUE);
            case 'f' -> readLiteral("false", Boolean.FALSE);
            case 'n' -> readLiteral("null", null);
            default -> readNumber();
        };
    }

    private Map<String, Object> readObject(int depth) {
        index++;
        skipWhitespace();
        Map<String, Object> object = new LinkedHashMap<>();
        if (consume('}')) {
            return object;
        }
        while (true) {
            skipWhitespace();
            if (index >= input.length() || input.charAt(index) != '"') {
                throw invalid();
            }
            String key = readString();
            skipWhitespace();
            if (!consume(':')) {
                throw invalid();
            }
            Object value = readValue(depth);
            if (object.containsKey(key)) {
                throw invalid();
            }
            object.put(key, value);
            skipWhitespace();
            if (consume('}')) {
                return object;
            }
            if (!consume(',')) {
                throw invalid();
            }
        }
    }

    private List<Object> readArray(int depth) {
        index++;
        skipWhitespace();
        List<Object> values = new ArrayList<>();
        if (consume(']')) {
            return values;
        }
        while (true) {
            values.add(readValue(depth));
            skipWhitespace();
            if (consume(']')) {
                return values;
            }
            if (!consume(',')) {
                throw invalid();
            }
        }
    }

    private String readString() {
        index++;
        StringBuilder value = new StringBuilder();
        while (index < input.length()) {
            char character = input.charAt(index++);
            if (character == '"') {
                return value.toString();
            }
            if (character < 0x20) {
                throw invalid();
            }
            if (character != '\\') {
                value.append(character);
                continue;
            }
            if (index >= input.length()) {
                throw invalid();
            }
            char escape = input.charAt(index++);
            switch (escape) {
                case '"', '\\', '/' -> value.append(escape);
                case 'b' -> value.append('\b');
                case 'f' -> value.append('\f');
                case 'n' -> value.append('\n');
                case 'r' -> value.append('\r');
                case 't' -> value.append('\t');
                case 'u' -> value.append(readUnicodeEscape());
                default -> throw invalid();
            }
        }
        throw invalid();
    }

    private char readUnicodeEscape() {
        if (index + 4 > input.length()) {
            throw invalid();
        }
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int digit = Character.digit(input.charAt(index++), 16);
            if (digit < 0) {
                throw invalid();
            }
            value = (value << 4) | digit;
        }
        return (char) value;
    }

    private Object readNumber() {
        int start = index;
        consume('-');
        if (consume('0')) {
            if (index < input.length() && Character.isDigit(input.charAt(index))) {
                throw invalid();
            }
        } else {
            readDigits(true);
        }
        if (consume('.')) {
            readDigits(true);
        }
        if (consume('e') || consume('E')) {
            if (!consume('+')) {
                consume('-');
            }
            readDigits(true);
        }
        if (start == index) {
            throw invalid();
        }
        try {
            return new BigDecimal(input.substring(start, index));
        } catch (NumberFormatException exception) {
            throw invalid();
        }
    }

    private void readDigits(boolean required) {
        int start = index;
        while (index < input.length() && input.charAt(index) >= '0' && input.charAt(index) <= '9') {
            index++;
        }
        if (required && start == index) {
            throw invalid();
        }
    }

    private Object readLiteral(String literal, Object value) {
        if (!input.startsWith(literal, index)) {
            throw invalid();
        }
        index += literal.length();
        return value;
    }

    private boolean consume(char expected) {
        if (index < input.length() && input.charAt(index) == expected) {
            index++;
            return true;
        }
        return false;
    }

    private void skipWhitespace() {
        while (index < input.length()) {
            char value = input.charAt(index);
            if (value != ' ' && value != '\t' && value != '\r' && value != '\n') {
                return;
            }
            index++;
        }
    }

    private IllegalArgumentException invalid() {
        return new IllegalArgumentException("report JSON is malformed");
    }
}
