package cz.upce.fei.ems.backend.validation.constraints;

import cz.upce.fei.ems.backend.validation.MaxUtf8BytesValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Limits a string by its UTF-8 byte count rather than its UTF-16 character count. */
@Documented
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE,
        ElementType.TYPE_USE, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxUtf8Bytes {
    String message() default "Value exceeds the maximum UTF-8 byte length";
    int value();
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
