package cz.upce.fei.ems.backend.validation;

import cz.upce.fei.ems.backend.dto.RegisterRequestDto;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class RegistrationRequestValidator implements ConstraintValidator<ValidRegistrationRequest, RegisterRequestDto> {
    private final int usernameMinLength;
    private final int usernameMaxLength;
    private final int passwordMinLength;

    public RegistrationRequestValidator(Environment environment) {
        usernameMinLength = environment.getProperty("registration.username.min-length", Integer.class, 3);
        usernameMaxLength = environment.getProperty("registration.username.max-length", Integer.class, 50);
        passwordMinLength = environment.getProperty("registration.password.min-length", Integer.class, 6);
    }

    @Override
    public boolean isValid(RegisterRequestDto request, ConstraintValidatorContext context) {
        if (request == null) return true;

        boolean valid = true;
        if (request.getUsername() != null && !request.getUsername().isBlank()
                && (request.getUsername().length() < usernameMinLength
                || request.getUsername().length() > usernameMaxLength)) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                            "Uživatelské jméno musí mít " + usernameMinLength + " až " + usernameMaxLength + " znaků")
                    .addPropertyNode("username")
                    .addConstraintViolation();
            valid = false;
        }

        if (request.getPassword() != null && !request.getPassword().isBlank()
                && request.getPassword().length() < passwordMinLength) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                            "Heslo musí mít alespoň " + passwordMinLength + " znaků")
                    .addPropertyNode("password")
                    .addConstraintViolation();
            valid = false;
        }
        return valid;
    }
}
