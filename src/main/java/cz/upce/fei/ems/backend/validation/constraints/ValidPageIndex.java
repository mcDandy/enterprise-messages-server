package cz.upce.fei.ems.backend.validation.constraints;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Min;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Min(value = 0, message = "Page index must be zero or greater")
@jakarta.validation.constraints.Max(value = 10_000, message = "Page index must not exceed 10000")
@Constraint(validatedBy = {})
@Target({ElementType.PARAMETER, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPageIndex {
    String message() default "Page index must be between 0 and 10000";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
