package de.tum.cit.aet.artemis.course.dto;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.lti.domain.LtiPlatformConfiguration;
import de.tum.cit.aet.artemis.lti.domain.OnlineCourseConfiguration;

/**
 * Online-course settings returned as part of an authorized course management response.
 *
 * @param id                       the optional configuration identifier
 * @param userPrefix               the account prefix used for LTI users
 * @param requireExistingUser      whether an existing account is required
 * @param ltiPlatformConfiguration the optional initialized platform configuration
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OnlineCourseConfigurationResponseDTO(@Nullable Long id, String userPrefix, boolean requireExistingUser,
        @Nullable LtiPlatformConfigurationResponseDTO ltiPlatformConfiguration) {

    /**
     * Maps online-course settings without the course back-reference.
     *
     * @param configuration the online-course configuration
     * @return the online-course response
     */
    public static OnlineCourseConfigurationResponseDTO of(OnlineCourseConfiguration configuration) {
        LtiPlatformConfiguration platform = configuration.getLtiPlatformConfiguration();
        var platformDTO = platform != null && Hibernate.isInitialized(platform) ? LtiPlatformConfigurationResponseDTO.of(platform) : null;
        return new OnlineCourseConfigurationResponseDTO(configuration.getId(), configuration.getUserPrefix(), configuration.isRequireExistingUser(), platformDTO);
    }
}
