package de.tum.cit.aet.artemis.service.plagiarism;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.repository.JpaRepository;

import de.tum.cit.aet.artemis.domain.Exercise;
import de.tum.cit.aet.artemis.domain.plagiarism.PlagiarismDetectionConfig;

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
}
