package cz.upce.fei.ems.backend.validation;

import cz.upce.fei.ems.backend.validation.constraints.MaxUtf8Bytes;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

/**
 * Checks a strings encoded UTF-8 byte length against its configured limit.
 */

public class MaxUtf8BytesValidator implements ConstraintValidator<MaxUtf8Bytes, String> {
    private int maximum;

    @Override
    public void initialize(MaxUtf8Bytes constraint) {
        maximum = constraint.value();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.getBytes(StandardCharsets.UTF_8).length <= maximum;
    }
}
