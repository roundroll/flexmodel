package com.roundroll.flex;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * An event-driven feature planner in the style of Jane Street's Flex model.
 * <p>
 * Raw input is a stream of events. A {@link Node} holds feature state and splits its computation in two: an
 * {@link Handle} runs on every event of a kind and updates the state, while an {@link Eval} computes the feature's
 * value on demand from that state and its declared dependencies. Because history lives inside the graph, handlers
 * must see every event: there is no coalescing and no cutoff.
 * <p>
 * At construction the planner derives, for each event class, one ordered plan of handlers and evals. The event
 * class selects the handlers; the values they declare in {@code deps} fix the order, since a value must be
 * computed before it is read. Plans are deduplicated (an action runs at most once per event), contain only what
 * the event actually needs, and run a node's handler before its eval when the node both handles the event and is
 * read by it. Dependency cycles and dependencies absent from the planner are rejected at construction.
 * <p>
 * After construction the planner is immutable apart from node state and computed values. Dispatching an event
 * walks a prebuilt array of bound method handles: no reflection, no allocation. A planner is not thread-safe;
 * dispatch events from a single thread.
 * <p>
 * Events are matched to handlers by exact class. Feature methods must be declared directly on the node's own
 * class (methods inherited from a superclass are not scanned); the node class must be accessible to the planner,
 * while the methods themselves may be private.
 */
public final class Planner implements Context {
    private final Node[] nodes;
    private final Map<Class<?>, Integer> indexByType;

    /**
     * Feature values, indexed by node registration order. Only {@link Eval} results are stored here, and they are
     * {@code double} by design: the Flex model's feature vectors are numeric, and primitive slots keep dispatch
     * allocation-free — an {@code Object[]} would box every primitive eval result. Non-numeric data belongs in node
     * state and is read through {@link Context#node(Class)}; note that {@code double} cannot exactly represent a
     * {@code long} above 2^53.
     */
    private final double[] values;
    private final Map<Class<?>, Action[]> eventPlans;
    private final Action[][] queryPlans;

    /**
     * Creates a planner over the given node instances.
     * <p>
     * Node order is irrelevant: plans are topological. Each node class may be registered once.
     *
     * @throws IllegalArgumentException if a node is duplicated, a feature method has an invalid signature, a
     *                                  declared dependency is not registered, or the graph has a cycle.
     */
    public Planner(final Node... nodes) {
        final var count = nodes.length;
        this.nodes = nodes.clone();
        this.values = new double[count];
        this.indexByType = buildIndexByTypeMap(nodes);
        final var nodeSpecs = buildSpecs(count);

        this.eventPlans = new HashMap<>(Math.max(16, count * 2));
        for (final var nodeSpec : nodeSpecs) {
            for (final var eventType : nodeSpec.handlers.keySet()) {
                if (!eventPlans.containsKey(eventType)) {
                    eventPlans.put(eventType, new PlanBuilder(nodeSpecs, indexByType).eventPlan(eventType));
                }
            }
        }

        this.queryPlans = new Action[count][];
        for (var i = 0; i < count; ++i) {
            if (nodeSpecs[i].eval != null) {
                queryPlans[i] = new PlanBuilder(nodeSpecs, indexByType).queryPlan(i);
            }
        }
    }

    private static IllegalArgumentException noEval(final Class<?> nodeType) {
        return new IllegalArgumentException(nodeType.getName() + " has no @Eval method");
    }

    private static void invokeHandler(final MethodHandle target, final Object event) {
        try {
            target.invokeExact(event);
        } catch (final Throwable t) {
            throw rethrow(t);
        }
    }

    private static double invokeEval(final MethodHandle target) {
        try {
            return (double) target.invokeExact();
        } catch (final Throwable t) {
            throw rethrow(t);
        }
    }

    private static RuntimeException rethrow(final Throwable t) {
        if (t instanceof final Error error) {
            throw error;
        }
        if (t instanceof final RuntimeException exception) {
            return exception;
        }
        return new IllegalStateException("Feature code failed", t);
    }

    private Map<Class<?>, Integer> buildIndexByTypeMap(final Node[] nodes) {
        final var count = nodes.length;
        final Map<Class<?>, Integer> indexByType;
        indexByType = new HashMap<>(Math.max(16, count * 2));
        for (var i = 0; i < count; ++i) {
            final var node = Objects.requireNonNull(nodes[i], "node");
            if (indexByType.putIfAbsent(node.getClass(), i) != null) {
                throw new IllegalArgumentException("Duplicate node type: " + node.getClass().getName());
            }
        }
        return indexByType;
    }

    private NodeSpec[] buildSpecs(final int count) {
        final NodeSpec[] specs;
        specs = new NodeSpec[count];
        for (var i = 0; i < count; ++i) {
            specs[i] = NodeSpec.inspect(nodes[i]);
        }
        for (final var spec : specs) {
            for (final var handler : spec.handlers.values()) {
                checkDeps(handler.deps, handler.label);
            }
            if (spec.eval != null) {
                checkDeps(spec.eval.deps, spec.eval.label);
            }
        }
        for (var i = 0; i < count; ++i) {
            final var spec = specs[i];
            for (final var handler : spec.handlers.values()) {
                handler.action = bindHandler(handler.method, spec.node);
            }
            if (spec.eval != null) {
                spec.eval.action = bindEval(spec.eval.method, spec.node, i);
            }
        }
        return specs;
    }

    /**
     * Runs the plan for the event's exact class. Events nobody handles are ignored.
     */
    public void on(final Object event) {
        final var plan = eventPlans.get(event.getClass());
        if (plan != null) {
            for (final var action : plan) {
                action.run(event);
            }
        }
    }

    /**
     * Runs the query plan for the node's eval and returns the resulting value.
     * <p>
     * Only evals run: nothing is dispatched, and repeated calls are idempotent as long as evals are pure.
     *
     * @throws IllegalArgumentException if the node is not registered or has no {@link Eval}.
     */
    public double feature(final Class<? extends Node> nodeType) {
        final var index = indexOf(nodeType);
        final var plan = queryPlans[index];
        if (plan == null) {
            throw noEval(nodeType);
        }
        for (final var action : plan) {
            action.run(null);
        }
        return values[index];
    }

    @Override
    @SuppressWarnings("unchecked")
    public <N extends Node> N node(final Class<N> nodeType) {
        return (N) nodes[indexOf(nodeType)];
    }

    @Override
    public double value(final Class<? extends Node> nodeType) {
        final var index = indexOf(nodeType);
        if (queryPlans[index] == null) {
            throw noEval(nodeType);
        }
        return values[index];
    }

    private int indexOf(final Class<?> nodeType) {
        final var index = indexByType.get(nodeType);
        if (index == null) {
            throw new IllegalArgumentException("Unknown node type: " + nodeType.getName());
        }
        return index;
    }

    private void checkDeps(final Class<?>[] deps, final String label) {
        for (final var dep : deps) {
            if (!indexByType.containsKey(dep)) {
                throw new IllegalArgumentException(
                    label + " depends on " + dep.getName() + ", which is not registered in this planner");
            }
        }
    }

    /**
     * Compiles a {@code void handler(Event, Context)} method into an {@link Action} that takes only the event.
     * The method handle is unreflected once, at construction, with private access, then its context argument and
     * node receiver are bound; the remaining event parameter is erased to {@code Object} so plans can hold
     * handlers for different event types in one array.
     */
    private Action bindHandler(final Method method, final Node node) {
        try {
            // Private access lets node classes keep their @Handle methods private.
            var target = MethodHandles.privateLookupIn(node.getClass(), MethodHandles.lookup())
                .unreflect(method);
            // Handler signature: (Node, Event, Context) -> void.
            target = MethodHandles.insertArguments(target, 2, this);
            target = MethodHandles.insertArguments(target, 0, node);
            final var bound = target.asType(MethodType.methodType(void.class, Object.class));
            return event -> invokeHandler(bound, event);
        } catch (final IllegalAccessException e) {
            throw new IllegalArgumentException("Cannot access " + method + "; node classes must be public", e);
        }
    }

    /**
     * Compiles a {@code double eval(Context)} method into an {@link Action} that stores the computed value in the
     * node's slot. As with handlers, the method handle is unreflected once at construction with private access,
     * then its context argument and node receiver are bound, leaving a {@code () -> double} call.
     */
    private Action bindEval(final Method method, final Node node, final int index) {
        try {
            // Private access lets node classes keep their @Eval methods private.
            var target = MethodHandles.privateLookupIn(node.getClass(), MethodHandles.lookup())
                .unreflect(method);
            // Eval signature: (Node, Context) -> double.
            target = MethodHandles.insertArguments(target, 1, this);
            final var bound = MethodHandles.insertArguments(target, 0, node);
            final var slots = values;
            return event -> slots[index] = invokeEval(bound);
        } catch (final IllegalAccessException e) {
            throw new IllegalArgumentException("Cannot access " + method + "; node classes must be public", e);
        }
    }
}
