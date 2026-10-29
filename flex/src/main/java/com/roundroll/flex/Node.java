package com.roundroll.flex;

/**
 * A node in the feature graph.
 * <p>
 * Nodes are plain classes owning mutable state. A node may declare any number of event handlers with
 * {@link Handle}, and at most one eval with {@link Eval}. Nodes are registered with a {@link Planner}, and each
 * node class may appear at most once in a planner.
 */
public interface Node {
}
