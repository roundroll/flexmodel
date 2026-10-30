package com.roundroll.flex;

import java.lang.reflect.Method;

final class EvalSpec {
    final Method method;
    final String label;
    Action action;

    EvalSpec(final Method method, final String label) {
        this.method = method;
        this.label = label;
    }
}
