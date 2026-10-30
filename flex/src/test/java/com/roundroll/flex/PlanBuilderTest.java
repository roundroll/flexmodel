package com.roundroll.flex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanBuilderTest {
    record Tick() {
    }

    record Unhandled() {
    }

    record Tock() {
    }

    static final class A implements Node {
        @Handle
        void onTick(final Tick tick, final Context context) {
        }

        @Eval
        double eval(final Context context) {
            return 0;
        }
    }

    @Deps(A.class)
    static final class B implements Node {
        @Handle
        void onTick(final Tick tick, final Context context) {
        }
    }

    @Deps(A.class)
    static final class C implements Node {
        @Handle
        void onTick(final Tick tick, final Context context) {
        }
    }

    static final class Source implements Node {
        @Handle
        void onTick(final Tick tick, final Context context) {
        }

        @Eval
        double eval(final Context context) {
            return 0;
        }
    }

    @Deps(Source.class)
    static final class Left implements Node {
        @Eval
        double eval(final Context context) {
            return 0;
        }
    }

    @Deps(Source.class)
    static final class Right implements Node {
        @Eval
        double eval(final Context context) {
            return 0;
        }
    }

    @Deps({Left.class, Right.class})
    static final class Root implements Node {
        @Eval
        double eval(final Context context) {
            return 0;
        }
    }

    @Deps(Source.class)
    static final class Shared implements Node {
        @Handle
        void onTick(final Tick tick, final Context context) {
        }

        @Handle
        void onTock(final Tock tock, final Context context) {
        }

        @Eval
        double eval(final Context context) {
            return 0;
        }
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
        void onTick(final Tick tick, final Context context) {
        }
    }

    @Deps(HandleCycleA.class)
    static final class HandleCycleB implements Node {
        @Handle
        void onTick(final Tick tick, final Context context) {
        }
    }

    @Deps(SelfCycle.class)
    static final class SelfCycle implements Node {
        @Eval
        double eval(final Context context) {
            return 0;
        }
    }

    @Deps(CycleEntry.class)
    static final class CycleRoot implements Node {
        @Handle
        void onTick(final Tick tick, final Context context) {
        }
    }

    @Deps(CycleA.class)
    static final class CycleEntry implements Node {
        @Eval
        double eval(final Context context) {
            return 0;
        }
    }

    @Deps(CycleB.class)
    static final class CycleA implements Node {
        @Eval
        double eval(final Context context) {
            return 0;
        }
    }

    @Deps(CycleA.class)
    static final class CycleB implements Node {
        @Eval
        double eval(final Context context) {
            return 0;
        }
    }

    private static final class Fixture {
        final NodeSpec[] specs;
        final Map<Class<?>, Integer> index = new HashMap<>();
        final List<String> log = new ArrayList<>();

        Fixture(final Node... nodes) {
            specs = new NodeSpec[nodes.length];
            for (var i = 0; i < nodes.length; ++i) {
                specs[i] = NodeSpec.inspect(nodes[i]);
                index.put(nodes[i].getClass(), i);
                for (final var handler : specs[i].handlers.values()) {
                    final var label = handler.label;
                    handler.action = event -> log.add(label);
                }
                if (specs[i].eval != null) {
                    final var label = specs[i].eval.label;
                    specs[i].eval.action = event -> log.add(label);
                }
            }
        }

        PlanBuilder builder() {
            return new PlanBuilder(specs, index);
        }

        List<String> execute(final Action[] plan) {
            for (final var action : plan) {
                action.run(null);
            }
            return log;
        }
    }

    @Test
    void eventPlanOrdersDependenciesAndDedupesSharedValues() {
        final var fixture = new Fixture(new B(), new C(), new A());

        final var executed = fixture.execute(fixture.builder().eventPlan(Tick.class));

        assertThat(executed).containsExactly("A#onTick", "A#eval", "B#onTick", "C#onTick");
    }

    @Test
    void queryPlanRunsOnlyTheEvalClosureAndDedupesSharedValues() {
        final var fixture = new Fixture(new Root(), new Left(), new Right(), new Source());

        final var executed = fixture.execute(fixture.builder().queryPlan(0));

        assertThat(executed).containsExactly("Source#eval", "Left#eval", "Right#eval", "Root#eval");
    }

    @Test
    void nodeDepsApplyToEveryHandlerAndTheEval() {
        final var tickFixture = new Fixture(new Shared(), new Source());
        assertThat(tickFixture.execute(tickFixture.builder().eventPlan(Tick.class)))
                .containsExactly("Source#onTick", "Source#eval", "Shared#onTick");

        final var tockFixture = new Fixture(new Shared(), new Source());
        assertThat(tockFixture.execute(tockFixture.builder().eventPlan(Tock.class)))
                .containsExactly("Source#eval", "Shared#onTock");

        final var queryFixture = new Fixture(new Shared(), new Source());
        assertThat(queryFixture.execute(queryFixture.builder().queryPlan(0)))
                .containsExactly("Source#eval", "Shared#eval");
    }

    @Test
    void eventPlanIsEmptyForUnhandledEvent() {
        final var fixture = new Fixture(new A());

        assertThat(fixture.builder().eventPlan(Unhandled.class)).isEmpty();
    }

    @Test
    void evalCycleMessageNamesTheFullPath() {
        final var fixture = new Fixture(new EvalCycleA(), new EvalCycleB());

        assertThatThrownBy(() -> fixture.builder().queryPlan(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cyclic feature dependency: "
                        + "EvalCycleA#eval -> EvalCycleB#eval -> EvalCycleA#eval");
    }

    @Test
    void handlerCycleMessageNamesTheFullPath() {
        final var fixture = new Fixture(new HandleCycleA(), new HandleCycleB());

        assertThatThrownBy(() -> fixture.builder().eventPlan(Tick.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cyclic feature dependency: "
                        + "HandleCycleA#onTick -> HandleCycleB#onTick -> HandleCycleA#onTick");
    }

    @Test
    void queryPlanRejectsASelfDependency() {
        final var fixture = new Fixture(new SelfCycle());

        assertThatThrownBy(() -> fixture.builder().queryPlan(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cyclic feature dependency: SelfCycle#eval -> SelfCycle#eval");
    }

    @Test
    void cycleMessageTrimsThePathBeforeTheRepeatedLabel() {
        final var fixture = new Fixture(new CycleRoot(), new CycleEntry(), new CycleA(), new CycleB());

        assertThatThrownBy(() -> fixture.builder().eventPlan(Tick.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cyclic feature dependency: CycleA#eval -> CycleB#eval -> CycleA#eval");
    }
}
