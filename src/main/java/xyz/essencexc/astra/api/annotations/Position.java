package xyz.essencexc.astra.api.annotations;

import java.lang.annotation.*;

/**
 * A position in the world. Field type: {@code net.minecraft.core.BlockPos} (whole blocks) or
 * {@code net.minecraft.world.phys.Vec3} (exact). Shown as X / Y / Z boxes with a "Here" button
 * that fills in the player's current position.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Position {
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
}
