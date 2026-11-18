package xyz.essencexc.astra.api.annotations;

import java.lang.annotation.*;

/**
 * A dropdown. Field type: any {@code enum} (options come from the constants' {@code toString()}), or {@code int} index into {@link #options()}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Dropdown {
    /** Row title shown in the menu. */
    String title();

    /** Optional muted text under the title. */
    String description() default "";

    /** Section (left-hand list on the module page). Empty = the enclosing {@link Section} class, or "General". */
    String section() default "";

    /** Optional icon resource id, e.g. {@code "mymod:icons/fish.svg"}. */
    String icon() default "";

    /** Extra search terms. */
    String[] keywords() default {};

    /** Options for an {@code int} field. Ignored for enum fields. */
    String[] options() default {};
}
