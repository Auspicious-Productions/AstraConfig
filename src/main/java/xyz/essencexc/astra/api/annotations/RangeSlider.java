package xyz.essencexc.astra.api.annotations;

import java.lang.annotation.*;
import xyz.essencexc.astra.api.function.ValueFormatter;

/**
 * A two-knob slider returning a from–to range. Field type: {@code IntRange} or {@code FloatRange} (Astra's, in {@code api.value}).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface RangeSlider {
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

    float min() default 0f;

    float max() default 100f;

    float step() default 1f;

    /** Minimum distance between the two knobs. */
    float minSpread() default 0f;

    Class<? extends ValueFormatter> formatter() default ValueFormatter.class;
}
