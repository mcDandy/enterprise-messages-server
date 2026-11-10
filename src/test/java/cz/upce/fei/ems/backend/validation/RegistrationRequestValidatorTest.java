package cz.upce.fei.ems.backend.validation;

import cz.upce.fei.ems.backend.dto.RegisterRequestDto;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegistrationRequestValidatorTest {
    @Test
    void acceptsValuesAtConfiguredBoundaries() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("registration.username.min-length", "4")
                .withProperty("registration.username.max-length", "12")
                .withProperty("registration.password.min-length", "8");
        RegistrationRequestValidator validator = new RegistrationRequestValidator(environment);
        RegisterRequestDto request = request("user", "password");

        assertTrue(validator.isValid(request, mock(ConstraintValidatorContext.class)));
        request.setUsername("abcdefghijkl");
        assertTrue(validator.isValid(request, mock(ConstraintValidatorContext.class)));
    }

    @Test
    void rejectsValuesOutsideConfiguredLimits() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("registration.username.min-length", "4")
                .withProperty("registration.username.max-length", "12")
                .withProperty("registration.password.min-length", "8");
        RegistrationRequestValidator validator = new RegistrationRequestValidator(environment);
        ConstraintValidatorContext context = mock(ConstraintValidatorContext.class, RETURNS_DEEP_STUBS);
        RegisterRequestDto request = request("abc", "short");

        assertFalse(validator.isValid(request, context));
        verify(context).buildConstraintViolationWithTemplate("Uživatelské jméno musí mít 4 až 12 znaků");
        verify(context).buildConstraintViolationWithTemplate("Heslo musí mít alespoň 8 znaků");

        ConstraintValidatorContext longUsernameContext =
                mock(ConstraintValidatorContext.class, RETURNS_DEEP_STUBS);
        request.setUsername("this_username_is_too_long");
        request.setPassword("long-enough");
        assertFalse(validator.isValid(request, longUsernameContext));
        verify(longUsernameContext)
                .buildConstraintViolationWithTemplate("Uživatelské jméno musí mít 4 až 12 znaků");
    }

    private RegisterRequestDto request(String username, String password) {
        RegisterRequestDto request = new RegisterRequestDto();
        request.setUsername(username);
        request.setPassword(password);
        request.setPublicKey("public-key");
        return request;
    }
}
