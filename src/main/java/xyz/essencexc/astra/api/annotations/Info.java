package xyz.essencexc.astra.api.annotations;

import java.lang.annotation.*;

/**
 * Static hint text. Field type: {@code String} (the field's value is the text shown).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Info {
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

    InfoType type() default InfoType.INFO;
}
