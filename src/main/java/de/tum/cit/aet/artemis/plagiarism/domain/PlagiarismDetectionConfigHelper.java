package de.tum.cit.aet.artemis.plagiarism.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.repository.JpaRepository;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
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
     *
     * @param <T>        type of Exercise
     * @param exercise   exercise without plagiarism checks config
     * @param repository repository used for saving exercises of type T
     */
    public static <T extends Exercise> void createAndSaveDefaultIfNullAndCourseExercise(T exercise, JpaRepository<T, Long> repository) {
        if (exercise.isCourseExercise() && exercise.getPlagiarismDetectionConfig() == null) {
            log.info("Filling missing plagiarisms checks config: exerciseId={}, type={}.", exercise.getId(), exercise.getExerciseType());
            var config = PlagiarismDetectionConfig.createDefault();
            exercise.setPlagiarismDetectionConfig(config);
            repository.save(exercise);
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
     * Validates the plagiarism detection config of the given exercise. Throws a BadRequestAlertException if invalid.
     *
     * Rules:
     *  - similarityThreshold must be between 0 and 100 if config present
     *  - minimumScore must be between 0 and 100 if config present
     *  - minimumSize must be >= 0 if config present
     *  - continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod must be between 7 and 31 if config present
     *  - If continuousPlagiarismControlEnabled is true, config must not be null
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
