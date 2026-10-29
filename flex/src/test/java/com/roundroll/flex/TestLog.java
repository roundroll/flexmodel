package com.roundroll.flex;

import java.util.ArrayList;
import java.util.List;

/**
 * Records every handler and eval invocation so a dispatch can be asserted as an executed plan.
 */
final class TestLog {
    private static final List<String> ENTRIES = new ArrayList<>();

    private TestLog() {
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
