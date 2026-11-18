package xyz.essencexc.astra.api.annotations;

import java.lang.annotation.*;

/** Greys the option out while any of the named boolean options (field names in the same module) is off. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface DependsOn {
    String[] value();
}
