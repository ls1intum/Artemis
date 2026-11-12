package de.tum.cit.aet.artemis.metis.service;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.service.UserAiPreferenceService;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.metis.config.MetisEnabled;
import de.tum.cit.aet.artemis.metis.config.MetisProperties;
import de.tum.cit.aet.artemis.metis.domain.MetisFailureReason;

/**
 * Decides whether a student's recorded AI decision allows Metis to send their code to the configured endpoint.
 * <p>
 * Cloud AI consent covers every endpoint. Local AI consent covers an endpoint that runs on university-owned
 * infrastructure ({@code artemis.metis.llm.hosting: LOCAL}) and not a cloud endpoint. A student who opted out of AI, or
 * who has not decided yet, does not take part. The consent is checked before any repository of the student is read.
 */
@Conditional(MetisEnabled.class)
@Lazy
@Service
public class MetisAiConsentService {

    private final UserAiPreferenceService userAiPreferenceService;

    private final MetisProperties.Hosting hosting;

    public MetisAiConsentService(UserAiPreferenceService userAiPreferenceService, MetisProperties properties) {
        this.userAiPreferenceService = userAiPreferenceService;
        this.hosting = properties.llm().hosting();
    }

    /**
     * Whether a decision allows Metis to use the configured endpoint: cloud AI always, local AI only for an endpoint on
     * university-owned infrastructure, no AI and no decision never.
     *
     * @param decision the student's recorded decision, null if they have not decided
     * @return true if the student takes part
     */
    public boolean allows(@Nullable AiSelectionDecision decision) {
        if (decision == null) {
            return false;
        }
        return switch (decision) {
            case CLOUD_AI -> true;
            case LOCAL_AI -> hosting == MetisProperties.Hosting.LOCAL;
            case NO_AI -> false;
        };
    }

    /**
     * The recorded AI decision of a user.
     *
     * @param userId the user
     * @return the decision, or null if the user has not decided
     */
    @Nullable
    public AiSelectionDecision decisionOf(long userId) {
        return userAiPreferenceService.findDecision(userId);
    }

    /**
     * Whether the user's recorded decision allows Metis to use the configured endpoint.
     *
     * @param userId the user
     * @return true if the user takes part
     */
    public boolean hasConsent(long userId) {
        return allows(decisionOf(userId));
    }

    /**
     * The users whose decision allows Metis to use the configured endpoint, looked up in one query.
     *
     * @param userIds the users to check
     * @return the ids of the users who take part
     */
    public Set<Long> consentingUsers(Collection<Long> userIds) {
        Map<Long, AiSelectionDecision> decisions = userAiPreferenceService.findDecisions(userIds);
        return decisions.entrySet().stream().filter(entry -> allows(entry.getValue())).map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    /**
     * The reason shown to a student whose check was skipped for missing consent. The stored reason stays
     * {@code NO_AI_CONSENT}; a student who allowed local AI only while the endpoint is a cloud service sees
     * {@code CLOUD_AI_REQUIRED} instead, because asking them for a decision again would only lead to the same choice.
     *
     * @param decision the student's current decision, null if they have not decided
     * @return {@code CLOUD_AI_REQUIRED} for local AI consent under cloud hosting, {@code NO_AI_CONSENT} otherwise
     */
    public String skipReasonFor(@Nullable AiSelectionDecision decision) {
        if (decision == AiSelectionDecision.LOCAL_AI && hosting == MetisProperties.Hosting.CLOUD) {
            return MetisFailureReason.CLOUD_AI_REQUIRED.name();
        }
        return MetisFailureReason.NO_AI_CONSENT.name();
    }
}
