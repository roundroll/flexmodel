package com.roundroll.flex.cucumber;

import java.util.ArrayList;
import java.util.List;

/**
 * Records every handler and eval invocation so a dispatch can be asserted as an executed plan.
 */
final class ExecutionLog {
    private static final List<String> ENTRIES = new ArrayList<>();

    private ExecutionLog() {
    }

    static void add(final String entry) {
        ENTRIES.add(entry);
    }

    static List<String> take() {
        final var entries = List.copyOf(ENTRIES);
        clear();
        return entries;
    }

    static void clear() {
        ENTRIES.clear();
    }
}
