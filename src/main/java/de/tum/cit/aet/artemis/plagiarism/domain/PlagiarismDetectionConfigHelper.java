package de.tum.cit.aet.artemis.plagiarism.domain;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.PlagiarismDetectionConfigRepository;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismDetectionConfigDTO;
import de.tum.cit.aet.artemis.plagiarism.service.ContinuousPlagiarismControlService;

/**
 * A config class containing logic for filling missing PlagiarismDetectionConfig for exercises created before deployment of the cpc.
 *
 * @see ContinuousPlagiarismControlService
 * @see PlagiarismDetectionConfig
 */
public final class PlagiarismDetectionConfigHelper {

    private static final Logger log = LoggerFactory.getLogger(PlagiarismDetectionConfigHelper.class);

    private PlagiarismDetectionConfigHelper() {
    }

    /**
     * Ads missing plagiarism checks config for course exercises.
     * <p>
     * The exercise's slot has to reflect what is stored: a caller that did not just store or attach the configuration
     * attaches it first, otherwise a stored configuration would look missing and be replaced by the default.
     *
     * @param exercise   saved exercise whose slot carries its stored plagiarism checks config, or none
     * @param repository repository used for storing the default configuration
     */
    public static void createAndSaveDefaultIfNullAndCourseExercise(Exercise exercise, PlagiarismDetectionConfigRepository repository) {
        if (exercise.isCourseExercise() && exercise.getPlagiarismDetectionConfig() == null) {
            log.info("Filling missing plagiarisms checks config: exerciseId={}, type={}.", exercise.getId(), exercise.getExerciseType());
            try {
                repository.replaceFor(exercise, PlagiarismDetectionConfig.createDefault());
            }
            catch (DataIntegrityViolationException e) {
                // A concurrent request filled the same legacy exercise first. Its configuration is the one to report.
                repository.attachTo(exercise);
            }
        }
    }

    /**
     * Sets given parameters as corresponding values of plagiarism checks config in the given exercise.
     *
     * @param exercise            exercise with existing plagiarism checks config
     * @param similarityThreshold similarityThreshold to set for the given exercise
     * @param minimumScore        similarityThreshold to set for the given exercise
     * @param minimumSize         similarityThreshold to set for the given exercise
     */
    public static void updateWithTemporaryParameters(Exercise exercise, int similarityThreshold, int minimumScore, int minimumSize) {
        var config = new PlagiarismDetectionConfig();
        config.setSimilarityThreshold(similarityThreshold);
        config.setMinimumScore(minimumScore);
        config.setMinimumSize(minimumSize);
        exercise.setPlagiarismDetectionConfig(config);
    }

    /**
     * Applies the submitted plagiarism detection config to the given managed exercise before validation and persistence.
     *
     * Semantics:
     * - a null DTO leaves the existing config untouched (an omitted field preserves the current value);
     * - an existing config is updated in place, preserving its identity and avoiding orphan-removal DELETE/INSERT churn;
     * - a missing config is created from the DTO and attached to the exercise's slot.
     *
     * Nothing is stored here: the configuration holds the key to its exercise and is not part of it, so after saving the
     * exercise the caller stores the slot's configuration with {@code PlagiarismDetectionConfigRepository.replaceOrAttach},
     * which updates the stored row of that exercise in place and never leaves a replaced one behind.
     *
     * @param exercise  the managed exercise to update
     * @param configDto the submitted plagiarism detection config (or {@code null})
     */
    public static void applyToExercise(Exercise exercise, @Nullable PlagiarismDetectionConfigDTO configDto) {
        if (configDto == null) {
            return;
        }
        PlagiarismDetectionConfig existingConfig = exercise.getPlagiarismDetectionConfig();
        if (existingConfig != null) {
            configDto.applyTo(existingConfig);
        }
        else {
            exercise.setPlagiarismDetectionConfig(configDto.toEntity());
        }
    }

    /**
     * Validates the plagiarism detection config of the given exercise. Throws a BadRequestAlertException if invalid.
     *
     * Rules:
     * - similarityThreshold must be between 0 and 100 if config present
     * - minimumScore must be between 0 and 100 if config present
     * - minimumSize must be >= 0 if config present
     * - continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod must be between 7 and 31 if config present
     * - If continuousPlagiarismControlEnabled is true, config must not be null
     *
     * @param exercise   the exercise whose config should be validated
     * @param entityName entity name for error construction
     */
    public static void validatePlagiarismDetectionConfigOrThrow(Exercise exercise, String entityName) {
        var config = exercise.getPlagiarismDetectionConfig();
        if (config == null) {
            // allowed when CPC disabled
            return;
        }
        int similarityThreshold = config.getSimilarityThreshold();
        if (similarityThreshold < 0 || similarityThreshold > 100) {
            throw new BadRequestAlertException("Similarity threshold must be between 0 and 100", entityName, "invalidSimilarityThreshold");
        }
        int minimumScore = config.getMinimumScore();
        if (minimumScore < 0 || minimumScore > 100) {
            throw new BadRequestAlertException("Minimum score must be between 0 and 100", entityName, "invalidMinimumScore");
        }
        int minimumSize = config.getMinimumSize();
        if (minimumSize < 0) {
            throw new BadRequestAlertException("Minimum size must be >= 0", entityName, "invalidMinimumSize");
        }
        int responsePeriod = config.getContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod();
        if (responsePeriod < 7 || responsePeriod > 31) {
            throw new BadRequestAlertException("Response period must be between 7 and 31 days", entityName, "invalidResponsePeriod");
        }
    }
}
