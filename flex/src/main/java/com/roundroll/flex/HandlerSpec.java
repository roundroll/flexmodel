package com.roundroll.flex;

import java.lang.reflect.Method;

final class HandlerSpec {
    final Method method;
    final String label;
    Action action;

    HandlerSpec(final Method method, final String label) {
        this.method = method;
        this.label = label;
    }
}
