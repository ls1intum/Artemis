package de.tum.cit.aet.artemis.course.service.home;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.service.UserAiPreferenceService;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.course.config.CourseHomeAiProperties;

/** Always reads the authoritative preference; never infers consent from a cached user DTO. */
@Service
@Lazy
@Profile(PROFILE_CORE)
public class CourseHomeAiPolicyService {

    private final CourseHomeAiProperties properties;

    private final UserAiPreferenceService preferences;

    private final Optional<CourseHomeAiClient> client;

    public CourseHomeAiPolicyService(CourseHomeAiProperties properties, UserAiPreferenceService preferences, Optional<CourseHomeAiClient> client) {
        this.properties = properties;
        this.preferences = preferences;
        this.client = client;
    }

    /**
     * Checks whether current consent permits this explicitly configured provider.
     *
     * @param userId the student identifier
     * @return true when fresh consent allows the configured provider location
     */
    public boolean canGenerate(long userId) {
        if (client.isEmpty() || !properties.isConfigured()) {
            return false;
        }
        de.tum.cit.aet.artemis.core.domain.AiSelectionDecision decision;
        try {
            decision = preferences.findDecision(userId);
        }
        catch (org.springframework.dao.DataAccessException ex) {
            return false;
        }
        return decision == AiSelectionDecision.CLOUD_AI || (decision == AiSelectionDecision.LOCAL_AI && properties.providerLocation().equals("LOCAL"));
    }
}
