package com.roundroll.flex;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds one event or query plan with a DFS-based topological sort.
 * <p>
 * Vertices are actions: a node's handler and its eval are separate vertices, and each is emitted after every
 * vertex it depends on. Edges come from the declared handler/eval deps, plus an implicit handler-to-eval edge when
 * a node both handles the current event and is read by it. Marks start white, become gray ({@code VISITING}) while
 * on the DFS stack — revisiting a gray vertex means a cycle — and black ({@code DONE}) once emitted, which also
 * deduplicates values reached through multiple paths.
 * <p>
 * Only the handlers of the event, or the eval closure of a query, are visited, so the result is a topological
 * order of the minimal required subgraph. An instance is single-use: call {@link #eventPlan(Class)} or
 * {@link #queryPlan(int)} once; the marks and action lists are never reset.
 */
final class PlanBuilder {
    private static final byte VISITING = 1;
    private static final byte DONE = 2;

    private final NodeSpec[] specs;
    private final Map<Class<?>, Integer> indexByType;
    private final byte[] marks;
    private final List<Action> actions = new ArrayList<>();
    private final List<String> path = new ArrayList<>();

    PlanBuilder(final NodeSpec[] specs, final Map<Class<?>, Integer> indexByType) {
        this.specs = specs;
        this.indexByType = indexByType;
        this.marks = new byte[specs.length * 2];
    }

    Action[] eventPlan(final Class<?> eventType) {
        for (var i = 0; i < specs.length; ++i) {
            if (specs[i].handlers.containsKey(eventType)) {
                visitHandler(i, eventType);
            }
        }
        return actions.toArray(Action[]::new);
    }

    Action[] queryPlan(final int feature) {
        visitEval(feature, null);
        return actions.toArray(Action[]::new);
    }

    /**
     * Post-order DFS step: emits this node's handler for {@code eventType} after the values it reads, then marks it
     * {@code DONE}. Encountering a {@code VISITING} handler means the dependencies form a cycle.
     * <p>
     * Example with specs ordered {@code [B, C, A]}: B and C both handle {@code Tick} and depend on A's value; A
     * handles {@code Tick} and has an eval.
     * <pre>{@code
     * visitHandler(B)
     *   dep A -> visitEval(A, Tick)
     *              visitHandler(A) -> add H:A
     *              eval A          -> add E:A
     *   add H:B
     * visitHandler(C): dep A already DONE -> add H:C
     * visitHandler(A): already DONE -> skip
     * plan: [H:A, E:A, H:B, H:C]
     * }</pre>
     */
    private void visitHandler(final int index, final Class<?> eventType) {
        final var handler = specs[index].handlers.get(eventType);
        if (handler != null) {
            visitAction(index, eventType, handler.label, handler.deps, handler.action);
        }
    }

    /**
     * Post-order DFS step: emits the node's eval after its eval dependencies. On an event plan this first visits the
     * node's handler for that event, so a value read on an event its node also handles sees the handler's update;
     * query plans pass {@code null} and therefore run evals only.
     * <p>
     * Example: B's handler depends on A's value, and A both handles the same event and has an eval.
     * <pre>{@code
     * visitEval(A, Tick)
     *   visitHandler(A) -> add H:A   // handler before eval
     *   (A has no eval deps)
     *   add E:A
     * }</pre>
     * A state-only node with no eval stops after its handler. A query ({@code eventType == null}) skips the
     * handler and emits only the eval closure.
     */
    private void visitEval(final int index, final Class<?> eventType) {
        // A value read on an event its node also handles must see the handler's effect first.
        if (eventType != null) {
            visitHandler(index, eventType);
        }
        final var eval = specs[index].eval;
        if (eval != null) {
            visitAction(specs.length + index, eventType, eval.label, eval.deps, eval.action);
        }
    }

    /**
     * Shared post-order step for handlers and evals: skips an action already emitted, rejects one already on the
     * DFS stack, emits its dependencies first, then appends the action and marks it {@code DONE}.
     */
    private void visitAction(final int markIndex, final Class<?> eventType, final String label, final Class<?>[] deps, final Action action) {
        if (marks[markIndex] == DONE) {
            return;
        }
        if (marks[markIndex] == VISITING) {
            throw cycle(label);
        }
        marks[markIndex] = VISITING;
        path.add(label);
        for (final var dep : deps) {
            visitEval(indexByType.get(dep), eventType);
        }
        path.remove(path.size() - 1);
        marks[markIndex] = DONE;
        actions.add(action);
    }

    private IllegalArgumentException cycle(final String label) {
        final var start = Math.max(0, path.indexOf(label));
        final var message = new StringBuilder("Cyclic feature dependency: ");
        for (var i = start; i < path.size(); ++i) {
            message.append(path.get(i)).append(" -> ");
        }
        message.append(label);
        return new IllegalArgumentException(message.toString());
    }
}
