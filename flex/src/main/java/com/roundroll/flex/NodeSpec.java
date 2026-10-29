package com.roundroll.flex;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

final class NodeSpec {
    final Node node;
    final Map<Class<?>, HandlerSpec> handlers = new LinkedHashMap<>();
    EvalSpec eval;

    private NodeSpec(final Node node) {
        this.node = node;
    }

    static NodeSpec inspect(final Node node) {
        final var spec = new NodeSpec(node);
        for (final var method : node.getClass().getDeclaredMethods()) {
            final var handle = method.getAnnotation(Handle.class);
            final var eval = method.getAnnotation(Eval.class);
            if (handle == null && eval == null) {
                continue;
            }
            if (handle != null && eval != null) {
                throw new IllegalArgumentException("Method cannot be both @Handle and @Eval: " + method);
            }
            if (Modifier.isStatic(method.getModifiers())) {
                throw new IllegalArgumentException("Feature methods must not be static: " + method);
            }
            if (handle != null) {
                spec.addHandler(node, method, handle);
            } else {
                spec.addEval(node, method, eval);
            }
        }
        return spec;
    }

    private static Class<?> getEventType(final Method method) {
        final var parameters = method.getParameterTypes();
        if (method.getReturnType() != void.class || parameters.length != 2 || parameters[1] != Context.class) {
            throw new IllegalArgumentException(
                "@Handle method must be void name(Event event, Context context): " + method);
        }
        final var eventType = parameters[0];
        if (eventType.isPrimitive()) {
            throw new IllegalArgumentException("Event type must be a reference type: " + method);
        }
        return eventType;
    }

    private static String label(final Node node, final Method method) {
        return node.getClass().getSimpleName() + "#" + method.getName();
    }

    private void addHandler(final Node node, final Method method, final Handle handle) {
        final var eventType = getEventType(method);
        final var previous = handlers.putIfAbsent(
            eventType, new HandlerSpec(method, handle.deps(), label(node, method)));
        if (previous != null) {
            throw new IllegalArgumentException(
                "Duplicate handler for " + eventType.getName() + " on " + node.getClass().getName());
        }
    }

    private void addEval(final Node node, final Method method, final Eval eval) {
        final var parameters = method.getParameterTypes();
        if (method.getReturnType() != double.class || parameters.length != 1 || parameters[0] != Context.class) {
            throw new IllegalArgumentException(
                "@Eval method must be double name(Context context): " + method);
        }
        if (this.eval != null) {
            throw new IllegalArgumentException("Duplicate @Eval method on " + node.getClass().getName());
        }
        this.eval = new EvalSpec(method, eval.deps(), label(node, method));
    }
}
