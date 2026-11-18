package xyz.essencexc.astra.api.annotations;

import java.lang.annotation.*;

/**
 * A number input box. Field type: {@code int}, {@code float} or {@code double}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Number {
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

    float min() default -Float.MAX_VALUE;

    float max() default Float.MAX_VALUE;

    /** Unit shown after the value, e.g. {@code "ms"}. */
    String unit() default "";
}
