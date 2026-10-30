package com.roundroll.flex;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Definition errors are rejected when the planner is built, before any event is dispatched.
 */
class PlannerValidationTest {
    record Ping() {
    }

    static final class StateOnly implements Node {
    }

    static final class Ghost implements Node {
    }

    static final class DuplicateNode implements Node {
    }

    @Deps(EvalCycleB.class)
    static final class EvalCycleA implements Node {
        @Eval
        double eval(final Context context) {
            return 0;
        }
    }

    @Deps(EvalCycleA.class)
    static final class EvalCycleB implements Node {
        @Eval
        double eval(final Context context) {
            return 0;
        }
    }

    @Deps(HandleCycleB.class)
    static final class HandleCycleA implements Node {
        @Handle
        void onPing(final Ping ping, final Context context) {
        }
    }

    @Deps(HandleCycleA.class)
    static final class HandleCycleB implements Node {
        @Handle
        void onPing(final Ping ping, final Context context) {
        }
    }

    @Deps(Ghost.class)
    static final class UsesGhost implements Node {
        @Eval
        double eval(final Context context) {
            return 0;
        }
    }

    static final class BadArity implements Node {
        @Handle
        void onPing(final Ping ping) {
        }
    }

    static final class BadHandlerReturn implements Node {
        @Handle
        int onPing(final Ping ping, final Context context) {
            return 0;
        }
    }

    static final class BadHandlerContext implements Node {
        @Handle
        void onPing(final Ping ping, final Object context) {
        }
    }

    static final class BadEvalReturn implements Node {
        @Eval
        int eval(final Context context) {
            return 0;
        }
    }

    static final class BadEvalParameters implements Node {
        @Eval
        double eval() {
            return 0;
        }
    }

    static final class StaticHandler implements Node {
        @Handle
        static void onPing(final Ping ping, final Context context) {
        }
    }

    static final class TwoEvals implements Node {
        @Eval
        double eval(final Context context) {
            return 0;
        }

        @Eval
        double evalAgain(final Context context) {
            return 0;
        }
    }

    static final class TwoHandlers implements Node {
        @Handle
        void first(final Ping ping, final Context context) {
        }

        @Handle
        void second(final Ping ping, final Context context) {
        }
    }

    static final class BothAnnotations implements Node {
        @Handle
        @Eval
        void onPing(final Ping ping, final Context context) {
        }
    }

    @Test
    void duplicateNodeTypesAreRejected() {
        assertThatThrownBy(() -> new Planner(new DuplicateNode(), new DuplicateNode()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate node type");
    }

    @Test
    void missingDependenciesAreRejected() {
        assertThatThrownBy(() -> new Planner(new UsesGhost()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(Ghost.class.getName())
                .hasMessageContaining("not registered");
    }

    @Test
    void evalCyclesAreRejected() {
        assertThatThrownBy(() -> new Planner(new EvalCycleA(), new EvalCycleB()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cyclic feature dependency")
                .hasMessageContaining("EvalCycleA#eval")
                .hasMessageContaining("EvalCycleB#eval");
    }

    @Test
    void handleCyclesAreRejected() {
        assertThatThrownBy(() -> new Planner(new HandleCycleA(), new HandleCycleB()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cyclic feature dependency")
                .hasMessageContaining("HandleCycleA#onPing")
                .hasMessageContaining("HandleCycleB#onPing");
    }

    @Test
    void invalidHandlerSignaturesAreRejected() {
        assertThatThrownBy(() -> new Planner(new BadArity()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@Handle method must be");
        assertThatThrownBy(() -> new Planner(new BadHandlerReturn()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@Handle method must be");
        assertThatThrownBy(() -> new Planner(new BadHandlerContext()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@Handle method must be");
        assertThatThrownBy(() -> new Planner(new StaticHandler()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be static");
    }

    @Test
    void invalidEvalSignaturesAreRejected() {
        assertThatThrownBy(() -> new Planner(new BadEvalReturn()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@Eval method must be");
        assertThatThrownBy(() -> new Planner(new BadEvalParameters()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@Eval method must be");
        assertThatThrownBy(() -> new Planner(new TwoEvals()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate @Eval");
        assertThatThrownBy(() -> new Planner(new BothAnnotations()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("both @Handle and @Eval");
    }

    @Test
    void duplicateHandlersForOneEventAreRejected() {
        assertThatThrownBy(() -> new Planner(new TwoHandlers()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate handler for");
    }

    @Test
    void featureOnANodeWithoutEvalIsRejected() {
        final var planner = new Planner(new StateOnly());

        assertThatThrownBy(() -> planner.feature(StateOnly.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no @Eval");
    }

    @Test
    void unknownNodeTypesAreRejected() {
        final var planner = new Planner(new StateOnly());

        assertThatThrownBy(() -> planner.value(Ghost.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown node type");
    }
}
