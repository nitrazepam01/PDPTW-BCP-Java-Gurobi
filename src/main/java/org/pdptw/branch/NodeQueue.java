package org.pdptw.branch;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;

public final class NodeQueue {
    private final PriorityQueue<BranchNode> nodes = new PriorityQueue<BranchNode>(
            Comparator.comparingDouble(BranchNode::lowerBound)
                    .thenComparingInt(BranchNode::depth)
                    .thenComparing(BranchNode::id));

    public void add(BranchNode node) {
        nodes.add(Objects.requireNonNull(node, "node"));
    }

    public Optional<BranchNode> poll() {
        return Optional.ofNullable(nodes.poll());
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    public int size() {
        return nodes.size();
    }
}
