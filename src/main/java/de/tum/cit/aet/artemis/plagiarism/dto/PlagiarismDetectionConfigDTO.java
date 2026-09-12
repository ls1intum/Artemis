package de.tum.cit.aet.artemis.plagiarism.dto;

import java.io.Serializable;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfig;

/**
 * DTO holding the plagiarism detection configuration of an exercise.
 * Dumb DTO: only scalar values, no entity references.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismDetectionConfigDTO(Long id, boolean continuousPlagiarismControlEnabled, boolean continuousPlagiarismControlPostDueDateChecksEnabled,
        int continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod, int similarityThreshold, int minimumScore, int minimumSize) implements Serializable {

    /**
     * Creates a {@link PlagiarismDetectionConfigDTO} from the given {@link PlagiarismDetectionConfig}.
     *
     * @param config the entity to convert (may be {@code null})
     * @return the corresponding DTO, or {@code null} if the input was {@code null}
     */
    public static PlagiarismDetectionConfigDTO of(PlagiarismDetectionConfig config) {
        return Optional.ofNullable(config)
                .map(c -> new PlagiarismDetectionConfigDTO(c.getId(), c.isContinuousPlagiarismControlEnabled(), c.isContinuousPlagiarismControlPostDueDateChecksEnabled(),
                        c.getContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod(), c.getSimilarityThreshold(), c.getMinimumScore(), c.getMinimumSize()))
                .orElse(null);
    }

    /**
     * Returns the same configuration without the row id, for payloads that are written to a file and read back by
     * another instance, which must not adopt the id of this exercise's configuration.
     *
     * @return a copy of this DTO with a {@code null} id
     */
    public PlagiarismDetectionConfigDTO withoutId() {
        return new PlagiarismDetectionConfigDTO(null, continuousPlagiarismControlEnabled, continuousPlagiarismControlPostDueDateChecksEnabled,
                continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod, similarityThreshold, minimumScore, minimumSize);
    }

    /**
     * Creates a new {@link PlagiarismDetectionConfig} entity carrying the values of this DTO.
     *
     * @return a new, transient plagiarism detection config entity
     */
    public PlagiarismDetectionConfig toEntity() {
        PlagiarismDetectionConfig config = new PlagiarismDetectionConfig();
        applyTo(config);
        return config;
    }

    /**
     * Copies the scalar values of this DTO onto the given managed {@link PlagiarismDetectionConfig} entity in place.
     * This preserves the entity's identity (and thereby its database row), avoiding orphan removal of an existing
     * {@code @OneToOne} association during an update.
     *
     * @param config the existing config entity to update (must not be {@code null})
     */
    public void applyTo(PlagiarismDetectionConfig config) {
        config.setContinuousPlagiarismControlEnabled(continuousPlagiarismControlEnabled);
        config.setContinuousPlagiarismControlPostDueDateChecksEnabled(continuousPlagiarismControlPostDueDateChecksEnabled);
        config.setContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod(continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod);
        config.setSimilarityThreshold(similarityThreshold);
        config.setMinimumScore(minimumScore);
        config.setMinimumSize(minimumSize);
    }
}
