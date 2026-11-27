package cz.upce.fei.ems.backend.validation.constraints;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Restricts a page size to the inclusive range from 1 through 100. */
@Documented
@Min(value = 1, message = "Page size must be at least 1")
@Max(value = 100, message = "Page size must not exceed 100")
@Constraint(validatedBy = {})
@Target({ElementType.PARAMETER, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPageSize {
    String message() default "Page size must be between 1 and 100";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
