package com.roundroll.flex;

/**
 * The view of the graph available to handlers and evals while a plan runs.
 */
public interface Context {
    /**
     * Returns the node of the given type, for reading or mutating its state.
     *
     * @throws IllegalArgumentException if no node of this type is registered.
     */
    <N extends Node> N node(Class<N> nodeType);

    /**
     * Returns the last value computed by the given node's eval.
     * <p>
     * The value is fresh only if the caller's node declares the dependency with {@link Deps}; the planner then
     * schedules that eval before any handler or eval of the node runs. Dependencies are declared, not enforced.
     *
     * @throws IllegalArgumentException if no node of this type is registered, or it has no {@link Eval}.
     */
    double value(Class<? extends Node> nodeType);
}
