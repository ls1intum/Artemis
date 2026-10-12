package de.tum.cit.aet.artemis.lti;

import static de.tum.cit.aet.artemis.lti.domain.LtiPlatformConfiguration.MAX_CUSTOM_NAME_LENGTH;
import static de.tum.cit.aet.artemis.lti.domain.LtiPlatformConfiguration.MAX_FIELD_LENGTH;
import static de.tum.cit.aet.artemis.lti.domain.OnlineCourseConfiguration.MAX_USER_PREFIX_LENGTH;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.lti.domain.LtiPlatformConfiguration;
import de.tum.cit.aet.artemis.lti.domain.OnlineCourseConfiguration;
import de.tum.cit.aet.artemis.lti.dto.LtiPlatformConfigurationDTO;
import de.tum.cit.aet.artemis.lti.dto.LtiPlatformConfigurationUpdateDTO;
import de.tum.cit.aet.artemis.lti.dto.OnlineCourseConfigurationDTO;

/**
 * Tests the input length and required-field validation of the LTI platform and online course configurations. An over-long value must be rejected
 * by the bean validation (a clean 400 with a field error) instead of failing at the database column limit.
 */
class LtiConfigurationValidationTest {

    private static ValidatorFactory validatorFactory;

    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    private static String repeat(int length) {
        return "a".repeat(length);
    }

    private static LtiPlatformConfigurationUpdateDTO updateDto(String registrationId, String clientId, String originalUrl, String customName, String authorizationUri,
            String jwkSetUri, String tokenUri) {
        return new LtiPlatformConfigurationUpdateDTO(null, registrationId, clientId, originalUrl, customName, authorizationUri, jwkSetUri, tokenUri);
    }

    private static LtiPlatformConfigurationUpdateDTO validUpdateDto() {
        return updateDto("artemis-1", "client", "https://moodle.example.com", "Moodle", "https://moodle.example.com/auth", "https://moodle.example.com/jwks",
                "https://moodle.example.com/token");
    }

    private static Set<String> violatedFields(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream().map(violation -> violation.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    @Test
    void platformUpdateDto_validValuesAtTheLimit_hasNoViolations() {
        var dto = updateDto(repeat(MAX_FIELD_LENGTH), repeat(MAX_FIELD_LENGTH), repeat(MAX_FIELD_LENGTH), repeat(MAX_CUSTOM_NAME_LENGTH), repeat(MAX_FIELD_LENGTH),
                repeat(MAX_FIELD_LENGTH), repeat(MAX_FIELD_LENGTH));

        assertThat(validator.validate(dto)).isEmpty();
        assertThat(validator.validate(validUpdateDto())).isEmpty();
    }

    @Test
    void platformUpdateDto_everyFieldOneCharacterOverTheLimit_isRejected() {
        var dto = updateDto(repeat(MAX_FIELD_LENGTH + 1), repeat(MAX_FIELD_LENGTH + 1), repeat(MAX_FIELD_LENGTH + 1), repeat(MAX_CUSTOM_NAME_LENGTH + 1),
                repeat(MAX_FIELD_LENGTH + 1), repeat(MAX_FIELD_LENGTH + 1), repeat(MAX_FIELD_LENGTH + 1));

        assertThat(violatedFields(validator.validate(dto))).containsExactlyInAnyOrder("registrationId", "clientId", "originalUrl", "customName", "authorizationUri", "jwkSetUri",
                "tokenUri");
    }

    @Test
    void platformUpdateDto_missingOrBlankCustomName_isRejected() {
        var withoutName = updateDto("artemis-1", "client", null, null, "https://a", "https://b", "https://c");
        var blankName = updateDto("artemis-1", "client", null, "   ", "https://a", "https://b", "https://c");

        assertThat(violatedFields(validator.validate(withoutName))).containsExactly("customName");
        assertThat(violatedFields(validator.validate(blankName))).containsExactly("customName");
    }

    @Test
    void platformDto_overLongFields_areRejectedButMissingCustomNameIsStillAllowed() {
        var legacyPlatformWithoutName = new LtiPlatformConfigurationDTO(1L, "artemis-1", "client", null, null, "https://a", "https://b", "https://c");
        var tooLong = new LtiPlatformConfigurationDTO(1L, "artemis-1", repeat(MAX_FIELD_LENGTH + 1), null, repeat(MAX_CUSTOM_NAME_LENGTH + 1), "https://a", "https://b",
                "https://c");

        assertThat(validator.validate(legacyPlatformWithoutName)).isEmpty();
        assertThat(violatedFields(validator.validate(tooLong))).containsExactlyInAnyOrder("clientId", "customName");
    }

    @Test
    void platformEntity_overLongFields_areRejected() {
        var platform = new LtiPlatformConfiguration();
        platform.setRegistrationId(repeat(MAX_FIELD_LENGTH + 1));
        platform.setClientId(repeat(MAX_FIELD_LENGTH));
        platform.setOriginalUrl(repeat(MAX_FIELD_LENGTH + 1));
        platform.setCustomName(repeat(MAX_CUSTOM_NAME_LENGTH + 1));
        platform.setAuthorizationUri(repeat(MAX_FIELD_LENGTH + 1));
        platform.setJwkSetUri(repeat(MAX_FIELD_LENGTH + 1));
        platform.setTokenUri(repeat(MAX_FIELD_LENGTH + 1));

        assertThat(violatedFields(validator.validate(platform))).containsExactlyInAnyOrder("registrationId", "originalUrl", "customName", "authorizationUri", "jwkSetUri",
                "tokenUri");
    }

    @Test
    void onlineCourseConfigurationDto_userPrefix_isBoundedAndNotBlank() {
        assertThat(validator.validate(new OnlineCourseConfigurationDTO(1L, repeat(MAX_USER_PREFIX_LENGTH), true, null))).isEmpty();
        assertThat(violatedFields(validator.validate(new OnlineCourseConfigurationDTO(1L, repeat(MAX_USER_PREFIX_LENGTH + 1), true, null)))).containsExactly("userPrefix");
        assertThat(violatedFields(validator.validate(new OnlineCourseConfigurationDTO(1L, " ", true, null)))).containsExactly("userPrefix");
    }

    @Test
    void onlineCourseConfigurationEntity_overLongUserPrefix_isRejected() {
        var configuration = new OnlineCourseConfiguration();
        configuration.setUserPrefix(repeat(MAX_USER_PREFIX_LENGTH + 1));

        assertThat(violatedFields(validator.validate(configuration))).containsExactly("userPrefix");
    }

    @Test
    void userPrefixLimit_leavesRoomForSeparatorAndUserNameInALogin() {
        assertThat(MAX_USER_PREFIX_LENGTH + "_".length()).isLessThan(Constants.USERNAME_MAX_LENGTH);
    }
}
