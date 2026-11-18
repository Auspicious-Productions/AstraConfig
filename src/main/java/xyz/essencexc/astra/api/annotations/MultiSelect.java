package xyz.essencexc.astra.api.annotations;

import java.lang.annotation.*;

/**
 * Pick any number of values. Field type: a {@code Set}, {@code EnumSet} or {@code List} of an
 * {@code enum} (choices are its constants), or a {@code List<String>} with {@link #options()}.
 * Astra replaces the field with a new collection when the selection changes.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface MultiSelect {
    /** Row title shown in the menu. */
    String title();

    /** Optional muted text under the title. */
    String description() default "";

    /** Section (left-hand list on the module page). Empty = the enclosing {@link Section} class, or "General". */
    String section() default "";

    /** Optional icon resource id. */
    String icon() default "";

    /** Extra search terms. */
    String[] keywords() default {};

    /** Choices for a {@code List<String>} field. Ignored for enum collections. */
    String[] options() default {};
}
