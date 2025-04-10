package de.tum.cit.aet.artemis.core.dto.validator;

import java.util.Base64;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class Base64UrlValidator implements ConstraintValidator<Base64Url, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true;
        }

        try {
            Base64.getUrlDecoder().decode(value);
            return true;
        }
        catch (IllegalArgumentException e) {
            return false;
        }
    }
}
