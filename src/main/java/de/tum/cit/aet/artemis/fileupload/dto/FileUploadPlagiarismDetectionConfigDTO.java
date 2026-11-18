package de.tum.cit.aet.artemis.fileupload.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfig;

/**
 * DTO for plagiarism-detection settings of a file upload exercise.
 *
 * @param continuousPlagiarismControlEnabled                             whether continuous plagiarism control is enabled
 * @param continuousPlagiarismControlPostDueDateChecksEnabled            whether checks continue after the due date
 * @param continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod the student response period in days
 * @param similarityThreshold                                            the minimum similarity percentage
 * @param minimumScore                                                   the minimum submission score
 * @param minimumSize                                                    the minimum submission size
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FileUploadPlagiarismDetectionConfigDTO(@Nullable Boolean continuousPlagiarismControlEnabled, @Nullable Boolean continuousPlagiarismControlPostDueDateChecksEnabled,
        @Nullable Integer continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod, @Nullable Integer similarityThreshold, @Nullable Integer minimumScore,
        @Nullable Integer minimumSize) {

    /**
     * Creates a response DTO from a plagiarism-detection configuration.
     *
     * @param config the configuration to map
     * @return the mapped configuration
     */
    public static FileUploadPlagiarismDetectionConfigDTO of(PlagiarismDetectionConfig config) {
        return new FileUploadPlagiarismDetectionConfigDTO(config.isContinuousPlagiarismControlEnabled(), config.isContinuousPlagiarismControlPostDueDateChecksEnabled(),
                config.getContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod(), config.getSimilarityThreshold(), config.getMinimumScore(), config.getMinimumSize());
    }

    /**
     * Creates a new configuration and overlays all values supplied by the request onto the current defaults.
     *
     * @return a new plagiarism-detection configuration
     */
    public PlagiarismDetectionConfig toEntity() {
        PlagiarismDetectionConfig config = PlagiarismDetectionConfig.createDefault();
        if (continuousPlagiarismControlEnabled != null) {
            config.setContinuousPlagiarismControlEnabled(continuousPlagiarismControlEnabled);
        }
        if (continuousPlagiarismControlPostDueDateChecksEnabled != null) {
            config.setContinuousPlagiarismControlPostDueDateChecksEnabled(continuousPlagiarismControlPostDueDateChecksEnabled);
        }
        if (continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod != null) {
            config.setContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod(continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod);
        }
        if (similarityThreshold != null) {
            config.setSimilarityThreshold(similarityThreshold);
        }
        if (minimumScore != null) {
            config.setMinimumScore(minimumScore);
        }
        if (minimumSize != null) {
            config.setMinimumSize(minimumSize);
        }
        return config;
    }
}
