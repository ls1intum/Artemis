package de.tum.cit.aet.artemis.iris.service.pyris.job;

import java.util.Arrays;
import java.util.Optional;

/**
 * The kind of a Pyris job, as written into the callback token. Binding the token to the kind keeps a token issued for
 * one kind of run from being accepted for a run of another kind with the same id.
 */
public enum PyrisJobType {

    CHAT("chat"), COMPETENCY_EXTRACTION("competency-extraction"), TUTOR_SUGGESTION("tutor-suggestion"), AUTONOMOUS_TUTOR("autonomous-tutor"),
    STRUGGLE_INTERVENTION("struggle-intervention"), GLOBAL_SEARCH_ANSWER("global-search-answer"), LECTURE_INGESTION("lecture-ingestion"), FAQ_INGESTION("faq-ingestion");

    private final String claimValue;

    PyrisJobType(String claimValue) {
        this.claimValue = claimValue;
    }

    /**
     * @return the value written into the token's job claim
     */
    public String claimValue() {
        return claimValue;
    }

    /**
     * @return whether runs of this kind are ingestion runs, which can run for hours
     */
    public boolean isIngestion() {
        return this == LECTURE_INGESTION || this == FAQ_INGESTION;
    }

    /**
     * @param job the job
     * @return the kind of the job
     */
    public static PyrisJobType of(PyrisJob job) {
        return switch (job) {
            case ChatJob ignored -> CHAT;
            case CompetencyExtractionJob ignored -> COMPETENCY_EXTRACTION;
            case TutorSuggestionJob ignored -> TUTOR_SUGGESTION;
            case AutonomousTutorJob ignored -> AUTONOMOUS_TUTOR;
            case StruggleInterventionJob ignored -> STRUGGLE_INTERVENTION;
            case GlobalSearchAnswerJob ignored -> GLOBAL_SEARCH_ANSWER;
            case LectureIngestionWebhookJob ignored -> LECTURE_INGESTION;
            case FaqIngestionWebhookJob ignored -> FAQ_INGESTION;
            default -> throw new IllegalArgumentException("Unknown Pyris job type " + job.getClass().getSimpleName());
        };
    }

    /**
     * @param claimValue the value of a token's job claim
     * @return the matching kind, or empty for an unknown value
     */
    public static Optional<PyrisJobType> fromClaimValue(String claimValue) {
        return Arrays.stream(values()).filter(type -> type.claimValue.equals(claimValue)).findFirst();
    }
}
