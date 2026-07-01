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
}
