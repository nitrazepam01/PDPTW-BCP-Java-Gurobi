package org.pdptw.branch;

enum NodeCutPropagationPolicy {
    NO_AUTOMATIC_CUTS(false, false),
    NODE_LOCAL_AUTOMATIC_SR(true, false),
    GLOBAL_AUTOMATIC_SR(true, true);

    private final boolean separateSubsetRowsAtNodes;
    private final boolean publishSeparatedRowsToGlobalPool;

    NodeCutPropagationPolicy(boolean separateSubsetRowsAtNodes, boolean publishSeparatedRowsToGlobalPool) {
        this.separateSubsetRowsAtNodes = separateSubsetRowsAtNodes;
        this.publishSeparatedRowsToGlobalPool = publishSeparatedRowsToGlobalPool;
    }

    boolean separateSubsetRowsAtNodes() {
        return separateSubsetRowsAtNodes;
    }

    boolean publishSeparatedRowsToGlobalPool() {
        return publishSeparatedRowsToGlobalPool;
    }
}
