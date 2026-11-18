package xyz.essencexc.astra.api.annotations;

import java.lang.annotation.*;

/** Older field names of this option. A saved value stored under an old name is migrated on load. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface PreviousNames {
    String[] value();
}
