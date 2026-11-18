package xyz.essencexc.astra.api.annotations;

import java.lang.annotation.*;

/**
 * Put on a nested static class (or Kotlin {@code object}) inside a module: every option field in it
 * belongs to this section. Gives readable access like {@code FishingModule.Pond.edge}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Section {
    String value();

    String description() default "";
}
