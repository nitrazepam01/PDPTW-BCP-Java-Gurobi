package org.pdptw.core;

public final class Request {
    private final int id;
    private final int pickupVertexId;
    private final int deliveryVertexId;
    private final int demand;

    public Request(int id, int pickupVertexId, int deliveryVertexId, int demand) {
        this.id = id;
        this.pickupVertexId = pickupVertexId;
        this.deliveryVertexId = deliveryVertexId;
        this.demand = demand;
    }

    public int id() {
        return id;
    }

    public int pickupVertexId() {
        return pickupVertexId;
    }

    public int deliveryVertexId() {
        return deliveryVertexId;
    }

    public int demand() {
        return demand;
    }
}
