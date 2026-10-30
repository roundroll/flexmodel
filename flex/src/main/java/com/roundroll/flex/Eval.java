package com.roundroll.flex;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a node's eval.
 * <p>
 * The method must be declared {@code double name(Context context)}. An eval is a pure function of the node's state
 * and of the dependencies its node declares with {@link Deps}: it computes the node's value and runs only when that
 * value is needed, either by a handler subscribed to the current event or by a {@link Planner#feature(Class)} query.
 * A node has at most one eval; stateless features need only an eval.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Eval {
}
