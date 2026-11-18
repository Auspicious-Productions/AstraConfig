package xyz.essencexc.astra.api.annotations;

import java.lang.annotation.*;
import xyz.essencexc.astra.api.function.ValueFormatter;

/**
 * A numeric slider. Field type: {@code int}, {@code float} or {@code double}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Slider {
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

    /** Formats the displayed number. {@code ValueFormatter.class} = default formatting. */
    Class<? extends ValueFormatter> formatter() default ValueFormatter.class;

    /** Log scale: equal track distance per factor, for wide ranges like 1 to 1,000,000. Needs {@code min > 0}. */
    boolean logarithmic() default false;
}
