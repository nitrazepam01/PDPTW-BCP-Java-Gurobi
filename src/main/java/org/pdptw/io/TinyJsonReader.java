package org.pdptw.io;

import org.pdptw.core.Instance;
import org.pdptw.core.Vertex;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TinyJsonReader {
    public Instance read(Path path) throws IOException {
        Object value = new JsonParser(Files.readString(path)).parse();
        Map<String, Object> root = asObject(value);
        String name = root.get("name") instanceof String ? asString(root.get("name")) : path.getFileName().toString();
        int nRequests = asInt(requiredNumber(root, "nRequests", name, path));
        int vehicleCapacity = asInt(requiredNumber(root, "vehicleCapacity", name, path));
        int maxVehicles = asInt(requiredNumber(root, "maxVehicles", name, path));
        List<Vertex> vertices = readVertices(requiredArray(root, "vertices", name, path));
        double[][] travelCost = readMatrix(requiredArray(root, "travelCost", name, path));
        double[][] travelTime = readMatrix(requiredArray(root, "travelTime", name, path));
        Map<Integer, Double> requestDuals = new LinkedHashMap<>();
        double fleetDual = 0.0;
        if (root.containsKey("duals")) {
            Map<String, Object> duals = asObject(root.get("duals"));
            if (duals.containsKey("request")) {
                Map<String, Object> requests = asObject(duals.get("request"));
                for (Map.Entry<String, Object> entry : requests.entrySet()) {
                    requestDuals.put(Integer.parseInt(entry.getKey()), asDouble(entry.getValue()));
                }
            }
            if (duals.containsKey("fleet")) {
                fleetDual = asDouble(duals.get("fleet"));
            }
        }
        return new Instance(name, nRequests, vehicleCapacity, maxVehicles, vertices, travelCost, travelTime, requestDuals, fleetDual);
    }

    private static Object requiredNumber(Map<String, Object> root, String key, String name, Path path) {
        Object value = root.get(key);
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException("Fixture " + path + " (" + name
                    + ") is not a pricing instance: missing number '" + key
                    + "'. Use the dedicated audit/tests for metadata-only fixtures such as Tiny-C or Tiny-D.");
        }
        return value;
    }

    private static List<Object> requiredArray(Map<String, Object> root, String key, String name, Path path) {
        Object value = root.get(key);
        if (!(value instanceof List<?>)) {
            throw new IllegalArgumentException("Fixture " + path + " (" + name
                    + ") is not a pricing instance: missing array '" + key
                    + "'. Use the dedicated audit/tests for metadata-only fixtures such as Tiny-C or Tiny-D.");
        }
        return asArray(value);
    }

    private static List<Vertex> readVertices(List<Object> values) {
        List<Vertex> vertices = new ArrayList<>();
        for (Object value : values) {
            Map<String, Object> vertex = asObject(value);
            int id = asInt(vertex.get("id"));
            Vertex.Type type = Vertex.parseType(asString(vertex.get("type")));
            int request = vertex.containsKey("request") ? asInt(vertex.get("request")) : 0;
            vertices.add(new Vertex(
                    id,
                    type,
                    request,
                    asDouble(vertex.get("x")),
                    asDouble(vertex.get("y")),
                    asDouble(vertex.get("ready")),
                    asDouble(vertex.get("due")),
                    asDouble(vertex.get("service")),
                    asInt(vertex.get("demand"))));
        }
        return vertices;
    }

    private static double[][] readMatrix(List<Object> rows) {
        double[][] matrix = new double[rows.size()][];
        for (int i = 0; i < rows.size(); i++) {
            List<Object> row = asArray(rows.get(i));
            matrix[i] = new double[row.size()];
            for (int j = 0; j < row.size(); j++) {
                matrix[i][j] = asDouble(row.get(j));
            }
        }
        return matrix;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObject(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asArray(Object value) {
        return (List<Object>) value;
    }

    private static String asString(Object value) {
        return (String) value;
    }

    private static int asInt(Object value) {
        return ((Number) value).intValue();
    }

    private static double asDouble(Object value) {
        return ((Number) value).doubleValue();
    }

    private static final class JsonParser {
        private final String input;
        private int pos;

        JsonParser(String input) {
            this.input = input;
        }

        Object parse() {
            Object value = parseValue();
            skipWhitespace();
            if (pos != input.length()) {
                throw error("Unexpected trailing content");
            }
            return value;
        }

        private Object parseValue() {
            skipWhitespace();
            if (pos >= input.length()) {
                throw error("Unexpected end of input");
            }
            char ch = input.charAt(pos);
            if (ch == '{') {
                return parseObject();
            }
            if (ch == '[') {
                return parseArray();
            }
            if (ch == '"') {
                return parseString();
            }
            if (ch == 't' || ch == 'f') {
                return parseBoolean();
            }
            if (ch == 'n') {
                return parseNull();
            }
            return parseNumber();
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> object = new LinkedHashMap<>();
            skipWhitespace();
            if (peek('}')) {
                expect('}');
                return object;
            }
            while (true) {
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                object.put(key, value);
                skipWhitespace();
                if (peek('}')) {
                    expect('}');
                    return object;
                }
                expect(',');
            }
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> array = new ArrayList<>();
            skipWhitespace();
            if (peek(']')) {
                expect(']');
                return array;
            }
            while (true) {
                array.add(parseValue());
                skipWhitespace();
                if (peek(']')) {
                    expect(']');
                    return array;
                }
                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < input.length()) {
                char ch = input.charAt(pos++);
                if (ch == '"') {
                    return sb.toString();
                }
                if (ch == '\\') {
                    if (pos >= input.length()) {
                        throw error("Incomplete escape");
                    }
                    char escaped = input.charAt(pos++);
                    switch (escaped) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (pos + 4 > input.length()) {
                                throw error("Incomplete unicode escape");
                            }
                            String hex = input.substring(pos, pos + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                        }
                        default -> throw error("Unknown escape: " + escaped);
                    }
                } else {
                    sb.append(ch);
                }
            }
            throw error("Unterminated string");
        }

        private Boolean parseBoolean() {
            if (input.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (input.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw error("Invalid boolean");
        }

        private Object parseNull() {
            if (!input.startsWith("null", pos)) {
                throw error("Invalid null");
            }
            pos += 4;
            return null;
        }

        private Number parseNumber() {
            int start = pos;
            if (peek('-')) {
                pos++;
            }
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                pos++;
            }
            boolean floating = false;
            if (peek('.')) {
                floating = true;
                pos++;
                while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                    pos++;
                }
            }
            if (pos < input.length() && (input.charAt(pos) == 'e' || input.charAt(pos) == 'E')) {
                floating = true;
                pos++;
                if (pos < input.length() && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) {
                    pos++;
                }
                while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                    pos++;
                }
            }
            String text = input.substring(start, pos);
            return floating ? Double.parseDouble(text) : Long.parseLong(text);
        }

        private void expect(char expected) {
            skipWhitespace();
            if (pos >= input.length() || input.charAt(pos) != expected) {
                throw error("Expected '" + expected + "'");
            }
            pos++;
        }

        private boolean peek(char expected) {
            return pos < input.length() && input.charAt(pos) == expected;
        }

        private void skipWhitespace() {
            while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
                pos++;
            }
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at offset " + pos);
        }
    }
}
