package org.pdptw.core;

public final class Vertex {
    public enum Type {
        DEPOT_START,
        PICKUP,
        DELIVERY,
        DEPOT_END
    }

    private final int id;
    private final Type type;
    private final int requestId;
    private final double x;
    private final double y;
    private final double readyTime;
    private final double dueTime;
    private final double serviceTime;
    private final int demand;

    public Vertex(
            int id,
            Type type,
            int requestId,
            double x,
            double y,
            double readyTime,
            double dueTime,
            double serviceTime,
            int demand) {
        this.id = id;
        this.type = type;
        this.requestId = requestId;
        this.x = x;
        this.y = y;
        this.readyTime = readyTime;
        this.dueTime = dueTime;
        this.serviceTime = serviceTime;
        this.demand = demand;
    }

    public int id() {
        return id;
    }

    public Type type() {
        return type;
    }

    public int requestId() {
        return requestId;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double readyTime() {
        return readyTime;
    }

    public double dueTime() {
        return dueTime;
    }

    public double serviceTime() {
        return serviceTime;
    }

    public int demand() {
        return demand;
    }

    public boolean isPickup() {
        return type == Type.PICKUP;
    }

    public boolean isDelivery() {
        return type == Type.DELIVERY;
    }

    public boolean isCustomer() {
        return isPickup() || isDelivery();
    }

    public static Type parseType(String value) {
        return switch (value) {
            case "depot_start" -> Type.DEPOT_START;
            case "pickup" -> Type.PICKUP;
            case "delivery" -> Type.DELIVERY;
            case "depot_end" -> Type.DEPOT_END;
            default -> throw new IllegalArgumentException("Unknown vertex type: " + value);
        };
    }
}
