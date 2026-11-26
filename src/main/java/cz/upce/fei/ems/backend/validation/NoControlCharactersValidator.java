package cz.upce.fei.ems.backend.validation;

import cz.upce.fei.ems.backend.validation.constraints.NoControlCharacters;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NoControlCharactersValidator implements ConstraintValidator<NoControlCharacters, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;
        if (value.isEmpty()) return true;
        int first = value.codePointAt(0);
        int last = value.codePointBefore(value.length());
        if (isWhitespace(first) || isWhitespace(last)) return false;
        return value.codePoints().noneMatch(codePoint -> Character.isISOControl(codePoint)
                || isDangerousInvisible(codePoint));
    }

    private boolean isWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    private boolean isDangerousInvisible(int codePoint) {
        return codePoint == 0x200B || codePoint == 0xFEFF || codePoint == 0x061C
                || codePoint == 0x200E || codePoint == 0x200F
                || codePoint >= 0x202A && codePoint <= 0x202E
                || codePoint >= 0x2066 && codePoint <= 0x2069;
    }
}
