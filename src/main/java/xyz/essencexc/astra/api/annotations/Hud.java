package xyz.essencexc.astra.api.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A HUD element. Field type: a {@code HudElement} subclass (the field may be final). Drawn in game
 * while the module is enabled; its position, scale and visibility are saved. The row on the
 * module page has a show/hide switch and an "Edit" button that opens the HUD editor.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Hud {
    /** Row title shown in the menu. Defaults to the element's own title. */
    String title() default "";

    /** Optional muted text under the title. */
    String description() default "";

    /** Section (left-hand list on the module page). Empty = the enclosing {@link Section} class, or "General". */
    String section() default "";

    /** Optional icon resource id. */
    String icon() default "";

    /** Extra search terms. */
    String[] keywords() default {};
}
