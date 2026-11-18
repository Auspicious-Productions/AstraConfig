package xyz.essencexc.astra.api.annotations;

import java.lang.annotation.*;

/**
 * An editable list of strings (names, IDs, coordinates...). Field type: {@code List<String>}.
 * The player can add, edit, reorder and remove entries. Astra replaces the field with a new list
 * when it changes.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ItemList {
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

    /** Placeholder in the "add" box. */
    String placeholder() default "";

    /** Maximum number of entries. */
    int maxItems() default 100;

    /** Maximum length of one entry. */
    int maxLength() default 128;
}
