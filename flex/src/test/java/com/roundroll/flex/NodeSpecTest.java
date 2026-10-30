package com.roundroll.flex;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NodeSpecTest {
    record EventA() {
    }

    record EventB() {
    }

    static final class EmptyNode implements Node {
    }

    static final class DepNode implements Node {
    }

    @Deps(DepNode.class)
    static final class RichNode implements Node {
        @Handle
        void onA(final EventA event, final Context context) {
        }

        @Handle
        void onB(final EventB event, final Context context) {
        }

        @Eval
        double eval(final Context context) {
            return 0;
        }

        void plain() {
        }
    }

    @Deps(DepNode.class)
    static class BaseNode implements Node {
        @Handle
        void onBase(final EventA event, final Context context) {
        }

        @Eval
        double eval(final Context context) {
            return 0;
        }
    }

    static final class ChildNode extends BaseNode {
    }

    @Test
    void inspectPreservesNodeForEmptyNode() {
        final var node = new EmptyNode();

        final var spec = NodeSpec.inspect(node);

        assertThat(spec.node).isSameAs(node);
        assertThat(spec.deps).isEmpty();
        assertThat(spec.handlers).isEmpty();
        assertThat(spec.eval).isNull();
    }

    @Test
    void inspectDiscoversHandlersAndEvalMetadata() {
        final var node = new RichNode();

        final var spec = NodeSpec.inspect(node);

        assertThat(spec.node).isSameAs(node);
        assertThat(spec.deps).containsExactly(DepNode.class);
        assertThat(spec.handlers).containsOnlyKeys(EventA.class, EventB.class);

        final var handlerA = spec.handlers.get(EventA.class);
        assertThat(handlerA.method.getName()).isEqualTo("onA");
        assertThat(handlerA.label).isEqualTo("RichNode#onA");
        assertThat(handlerA.action).isNull();

        final var handlerB = spec.handlers.get(EventB.class);
        assertThat(handlerB.method.getName()).isEqualTo("onB");
        assertThat(handlerB.label).isEqualTo("RichNode#onB");
        assertThat(handlerB.action).isNull();

        assertThat(spec.eval).isNotNull();
        assertThat(spec.eval.method.getName()).isEqualTo("eval");
        assertThat(spec.eval.label).isEqualTo("RichNode#eval");
        assertThat(spec.eval.action).isNull();
    }

    @Test
    void inspectDoesNotScanInheritedFeatureMethodsOrDeps() {
        final var spec = NodeSpec.inspect(new ChildNode());

        assertThat(spec.deps).isEmpty();
        assertThat(spec.handlers).isEmpty();
        assertThat(spec.eval).isNull();
    }
}
