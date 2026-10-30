package com.roundroll.flex;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the values a node reads, shared by every handler and the eval.
 * <p>
 * The planner schedules the dependencies' evals before any handler or eval of this node runs, so they are fresh
 * when read with {@link Context#value(Class)}. There is one dependency list per node; handlers and evals do not
 * declare their own. A node without dependencies omits the annotation.
 * <p>
 * A declared dependency must be registered in the same planner. Depending on the node itself, directly or
 * transitively, is a cycle and is rejected when the planner is built. Like feature methods, the annotation is read
 * from the node's own class; it is not inherited.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Deps {
    /**
     * Node types whose values this node reads.
     */
    Class<? extends Node>[] value();
}
