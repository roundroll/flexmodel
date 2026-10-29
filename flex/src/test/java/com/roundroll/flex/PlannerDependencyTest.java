package com.roundroll.flex;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ordering rules inside a single event: handlers run before the evals that read their node, state reads are
 * ordered like value reads, and an undeclared read observes a stale value.
 */
class PlannerDependencyTest {
    record Tick() {
    }

    static final class Counter implements Node {
        int count;

        @Handle
        void onTick(final Tick tick, final Context context) {
            ++count;
            TestLog.add("H:Counter");
        }
    }

    static final class Snapshot implements Node {
        int seen = -1;

        @Handle(deps = Counter.class)
        void onTick(final Tick tick, final Context context) {
            seen = context.node(Counter.class).count;
            TestLog.add("H:Snapshot");
        }
    }

    static final class Level implements Node {
        int level;

        @Handle
        void onTick(final Tick tick, final Context context) {
            ++level;
            TestLog.add("H:Level");
        }

        @Eval
        double eval(final Context context) {
            TestLog.add("E:Level");
            return level;
        }
    }

    static final class Reader implements Node {
        double seen = -1;

        @Handle(deps = Level.class)
        void onTick(final Tick tick, final Context context) {
            seen = context.value(Level.class);
            TestLog.add("H:Reader");
        }
    }

    static final class StaleReader implements Node {
        double seen = -1;

        @Handle
        void onTick(final Tick tick, final Context context) {
            seen = context.value(Level.class);
            TestLog.add("H:Stale");
        }
    }

    static final class FreshReader implements Node {
        double seen = -1;

        @Handle(deps = Level.class)
        void onTick(final Tick tick, final Context context) {
            seen = context.value(Level.class);
            TestLog.add("H:Fresh");
        }
    }

    static final class PrivateFeature implements Node {
        private int hits;

        @Handle
        private void onTick(final Tick tick, final Context context) {
            ++hits;
        }

        @Eval
        private double eval(final Context context) {
            return hits;
        }
    }

    @BeforeEach
    void clearLog() {
        TestLog.clear();
    }

    @Test
    void handlerStateReadsAreOrderedRegardlessOfRegistrationOrder() {
        for (final var planner : List.of(
                new Planner(new Snapshot(), new Counter()),
                new Planner(new Counter(), new Snapshot()))) {
            for (var i = 0; i < 3; ++i) {
                planner.on(new Tick());
            }
            assertThat(planner.node(Snapshot.class).seen).isEqualTo(3);
        }
    }

    @Test
    void aNodesHandlerRunsBeforeTheEvalThatReadsIt() {
        for (final var planner : List.of(
                new Planner(new Reader(), new Level()),
                new Planner(new Level(), new Reader()))) {
            planner.on(new Tick());
            assertThat(planner.node(Reader.class).seen).isEqualTo(1.0);
            assertThat(TestLog.take()).containsExactly("H:Level", "E:Level", "H:Reader");

            planner.on(new Tick());
            assertThat(planner.node(Reader.class).seen).isEqualTo(2.0);
            assertThat(TestLog.take()).containsExactly("H:Level", "E:Level", "H:Reader");
        }
    }

    /**
     * Dependencies are declared, not enforced: {@code StaleReader} reads {@code Level}'s value without declaring it,
     * so the planner schedules no eval for it and it observes whatever the slot holds at its point in the plan.
     * <p>
     * The registration order is {@code [StaleReader, Level, FreshReader]}, so H:Stale runs before this tick's
     * E:Level and sees the previous tick's value (0, then 1), while the declaring FreshReader sees 1, then 2.
     * Staleness is order-dependent: after FreshReader has triggered the eval, an undeclared read would see the fresh
     * value. Declaring {@code deps = Level.class} is the fix.
     */
    @Test
    void undeclaredDependenciesAreStale() {
        final var planner = new Planner(new StaleReader(), new Level(), new FreshReader());

        planner.on(new Tick());
        assertThat(planner.node(FreshReader.class).seen).isEqualTo(1.0);
        assertThat(planner.node(StaleReader.class).seen).isEqualTo(0.0);   // eval not yet run for this tick

        planner.on(new Tick());
        assertThat(planner.node(FreshReader.class).seen).isEqualTo(2.0);
        assertThat(planner.node(StaleReader.class).seen).isEqualTo(1.0);   // previous tick's value
    }

    @Test
    void privateFeatureMethodsAreSupported() {
        final var planner = new Planner(new PrivateFeature());

        planner.on(new Tick());
        planner.on(new Tick());

        assertThat(planner.feature(PrivateFeature.class)).isEqualTo(2.0);
    }

    @Test
    void nodeReturnsTheRegisteredInstance() {
        final var level = new Level();

        final var planner = new Planner(level);

        assertThat(planner.node(Level.class)).isSameAs(level);
    }
}
