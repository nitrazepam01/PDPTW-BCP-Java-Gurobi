package org.pdptw.io;

import org.pdptw.core.Instance;
import org.pdptw.core.Vertex;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BenchmarkInstanceReader {
    public Instance read(Path path) throws IOException {
        String text = Files.readString(path);
        String trimmed = text.stripLeading();
        if (trimmed.startsWith("{")) {
            return new TinyJsonReader().read(path);
        }
        List<String> lines = nonBlankLines(text);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Empty instance file: " + path);
        }
        String[] header = tokens(lines.get(0));
        if (header.length == 5) {
            return readRopkeCordeau(path, lines, header);
        }
        if (header.length == 3) {
            return readLiLim(path, lines, header);
        }
        throw new IllegalArgumentException("Unsupported benchmark instance header in " + path
                + ": expected RC 5-field or LL 3-field header");
    }

    public static String formatName(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".json")) {
            return "tiny-json";
        }
        if (name.startsWith("aa") || name.startsWith("bb") || name.startsWith("cc")
                || name.startsWith("dd") || name.startsWith("xx") || name.startsWith("yy")) {
            return "rc-text";
        }
        if (name.startsWith("lc") || name.startsWith("lr") || name.startsWith("lrc")) {
            return "ll-text";
        }
        return "benchmark-text";
    }

    public static String paperGroup(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".json")) {
            return "tiny";
        }
        boolean reverse = name.endsWith("_reverse") || name.endsWith("_reverse.txt");
        if (name.startsWith("aa") || name.startsWith("bb") || name.startsWith("cc")
                || name.startsWith("dd")) {
            return reverse ? "RC_reverse" : "RC";
        }
        if (name.startsWith("xx") || name.startsWith("yy")) {
            return reverse ? "RC_extra_reverse" : "RC_extra";
        }
        if (name.startsWith("lc") || name.startsWith("lr") || name.startsWith("lrc")) {
            return reverse ? "LL_reverse" : "LL";
        }
        return "unknown";
    }

    private static Instance readRopkeCordeau(Path path, List<String> lines, String[] header) {
        int nRequests = parseInt(header[1], path, 1);
        int capacity = parseInt(header[3], path, 3);
        int endDepotId = 2 * nRequests + 1;
        if (lines.size() < endDepotId + 2) {
            throw new IllegalArgumentException("RC instance " + path + " has too few node rows");
        }
        ArrayList<Vertex> vertices = new ArrayList<Vertex>();
        for (int row = 1; row <= endDepotId + 1; row++) {
            String[] parts = tokens(lines.get(row));
            if (parts.length != 7) {
                throw new IllegalArgumentException("RC instance " + path + " row " + row
                        + " must have 7 fields");
            }
            int id = parseInt(parts[0], path, row);
            Vertex.Type type;
            int requestId;
            if (id == 0) {
                type = Vertex.Type.DEPOT_START;
                requestId = 0;
            } else if (id == endDepotId) {
                type = Vertex.Type.DEPOT_END;
                requestId = 0;
            } else if (id <= nRequests) {
                type = Vertex.Type.PICKUP;
                requestId = id;
            } else {
                type = Vertex.Type.DELIVERY;
                requestId = id - nRequests;
            }
            vertices.add(new Vertex(
                    id,
                    type,
                    requestId,
                    parseDouble(parts[1], path, row),
                    parseDouble(parts[2], path, row),
                    parseDouble(parts[5], path, row),
                    parseDouble(parts[6], path, row),
                    parseDouble(parts[3], path, row),
                    parseInt(parts[4], path, row)));
        }
        ensureContiguousIds(path, vertices, endDepotId);
        double[][] matrix = euclideanMatrix(vertices);
        return new Instance(
                path.getFileName().toString(),
                nRequests,
                capacity,
                nRequests,
                vertices,
                matrix,
                matrix,
                Collections.emptyMap(),
                0.0);
    }

    private static Instance readLiLim(Path path, List<String> lines, String[] header) {
        int maxVehicles = parseInt(header[0], path, 0);
        int capacity = parseInt(header[1], path, 1);
        LinkedHashMap<Integer, RawLlNode> rawNodes = new LinkedHashMap<Integer, RawLlNode>();
        for (int row = 1; row < lines.size(); row++) {
            String[] parts = tokens(lines.get(row));
            if (parts.length != 9) {
                throw new IllegalArgumentException("LL instance " + path + " row " + row
                        + " must have 9 fields");
            }
            RawLlNode node = new RawLlNode(
                    parseInt(parts[0], path, row),
                    parseDouble(parts[1], path, row),
                    parseDouble(parts[2], path, row),
                    parseInt(parts[3], path, row),
                    parseDouble(parts[4], path, row),
                    parseDouble(parts[5], path, row),
                    parseDouble(parts[6], path, row),
                    parseInt(parts[7], path, row),
                    parseInt(parts[8], path, row));
            rawNodes.put(Integer.valueOf(node.id), node);
        }
        RawLlNode depot = rawNodes.get(Integer.valueOf(0));
        if (depot == null) {
            throw new IllegalArgumentException("LL instance " + path + " is missing depot row 0");
        }
        Map<Integer, Integer> requestByVertex = new HashMap<Integer, Integer>();
        int requestId = 0;
        ArrayList<Integer> pickupIds = new ArrayList<Integer>();
        for (RawLlNode node : rawNodes.values()) {
            if (node.id != 0 && node.demand > 0 && node.deliveryId > 0) {
                pickupIds.add(Integer.valueOf(node.id));
            }
        }
        Collections.sort(pickupIds);
        for (Integer pickupId : pickupIds) {
            RawLlNode pickup = rawNodes.get(pickupId);
            RawLlNode delivery = rawNodes.get(Integer.valueOf(pickup.deliveryId));
            if (delivery == null || delivery.pickupId != pickup.id) {
                throw new IllegalArgumentException("LL instance " + path
                        + " has inconsistent pair " + pickup.id + " -> " + pickup.deliveryId);
            }
            requestId++;
            requestByVertex.put(Integer.valueOf(pickup.id), Integer.valueOf(requestId));
            requestByVertex.put(Integer.valueOf(delivery.id), Integer.valueOf(requestId));
        }
        int maxOriginalId = rawNodes.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        int endDepotId = maxOriginalId + 1;
        ArrayList<Vertex> vertices = new ArrayList<Vertex>();
        for (int id = 0; id <= maxOriginalId; id++) {
            RawLlNode node = rawNodes.get(Integer.valueOf(id));
            if (node == null) {
                throw new IllegalArgumentException("LL instance " + path
                        + " has non-contiguous node ids; missing " + id);
            }
            Vertex.Type type;
            int mappedRequestId = 0;
            if (id == 0) {
                type = Vertex.Type.DEPOT_START;
            } else if (node.demand > 0 && node.deliveryId > 0) {
                type = Vertex.Type.PICKUP;
                mappedRequestId = requestByVertex.get(Integer.valueOf(id)).intValue();
            } else if (node.demand < 0 && node.pickupId > 0) {
                type = Vertex.Type.DELIVERY;
                mappedRequestId = requestByVertex.get(Integer.valueOf(id)).intValue();
            } else {
                throw new IllegalArgumentException("LL instance " + path
                        + " contains unsupported non-depot node " + id);
            }
            vertices.add(new Vertex(
                    id,
                    type,
                    mappedRequestId,
                    node.x,
                    node.y,
                    node.ready,
                    node.due,
                    node.service,
                    node.demand));
        }
        vertices.add(new Vertex(
                endDepotId,
                Vertex.Type.DEPOT_END,
                0,
                depot.x,
                depot.y,
                depot.ready,
                depot.due,
                0.0,
                0));
        double[][] matrix = euclideanMatrix(vertices);
        return new Instance(
                path.getFileName().toString(),
                requestId,
                capacity,
                maxVehicles,
                vertices,
                matrix,
                matrix,
                Collections.emptyMap(),
                0.0);
    }

    private static double[][] euclideanMatrix(List<Vertex> vertices) {
        int size = vertices.size();
        Vertex[] byId = new Vertex[size];
        for (Vertex vertex : vertices) {
            byId[vertex.id()] = vertex;
        }
        double[][] matrix = new double[size][size];
        for (int from = 0; from < size; from++) {
            for (int to = 0; to < size; to++) {
                if (from == to) {
                    matrix[from][to] = 0.0;
                } else {
                    matrix[from][to] = distance(byId[from], byId[to]);
                }
            }
        }
        return matrix;
    }

    private static double distance(Vertex from, Vertex to) {
        double dx = from.x() - to.x();
        double dy = from.y() - to.y();
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static void ensureContiguousIds(Path path, List<Vertex> vertices, int maxId) {
        boolean[] seen = new boolean[maxId + 1];
        for (Vertex vertex : vertices) {
            if (vertex.id() < 0 || vertex.id() > maxId) {
                throw new IllegalArgumentException("Instance " + path + " has out-of-range vertex id "
                        + vertex.id());
            }
            seen[vertex.id()] = true;
        }
        for (int id = 0; id < seen.length; id++) {
            if (!seen[id]) {
                throw new IllegalArgumentException("Instance " + path + " is missing vertex id " + id);
            }
        }
    }

    private static List<String> nonBlankLines(String text) {
        ArrayList<String> lines = new ArrayList<String>();
        for (String line : text.split("\\R")) {
            if (!line.isBlank()) {
                lines.add(line.trim());
            }
        }
        return lines;
    }

    private static String[] tokens(String line) {
        return line.trim().split("\\s+");
    }

    private static int parseInt(String value, Path path, int field) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid integer in " + path + " field " + field
                    + ": " + value, exception);
        }
    }

    private static double parseDouble(String value, Path path, int field) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid number in " + path + " field " + field
                    + ": " + value, exception);
        }
    }

    private record RawLlNode(
            int id,
            double x,
            double y,
            int demand,
            double ready,
            double due,
            double service,
            int pickupId,
            int deliveryId) {
    }
}
