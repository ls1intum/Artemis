package de.tum.cit.aet.artemis.course.service.home;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
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

    record Selection(AiSelectionDecision decision, String preset) {
    }

    public boolean canGenerate(long userId) {
        return selection(userId).isPresent();
    }

    /** Reads the current preference and returns its configured route, if available. */
    Optional<Selection> selection(long userId) {
        if (client.isEmpty() || !properties.isConfigured()) {
            return Optional.empty();
        }
        try {
            var decision = preferences.findDecision(userId);
            if (decision == null) {
                return Optional.empty();
            }
            return properties.presetFor(decision).map(preset -> new Selection(decision, preset));
        }
        catch (DataAccessException ex) {
            return Optional.empty();
        }
    }
}
