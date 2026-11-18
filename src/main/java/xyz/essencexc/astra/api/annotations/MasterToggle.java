package xyz.essencexc.astra.api.annotations;

import java.lang.annotation.*;

/**
 * The module's on/off switch, shown on its card (not as a row). Field type: {@code boolean}.
 * While it is off, the module's {@code @EventHandler} methods are unsubscribed.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface MasterToggle {
}
