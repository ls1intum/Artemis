package de.tum.in.www1.artemis.service.plagiarism;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.repository.JpaRepository;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.plagiarism.*;

public final class PlagiarismDetectionConfigHelper {

    private static final Logger log = LoggerFactory.getLogger(PlagiarismDetectionConfigHelper.class);

    private PlagiarismDetectionConfigHelper() {
    }

    /**
     * Ads missing plagiarism checks config
     *
     * @param <T>        type of Exercise
     * @param exercise   exercise without plagiarism checks config
     * @param repository repository used for saving exercises of type T
     */
    public static <T extends Exercise> void createAndSaveDefaultIfNull(T exercise, JpaRepository<T, Long> repository) {
        if (exercise.getPlagiarismDetectionConfig() == null) {
            log.info("Filling missing plagiarisms checks config: exerciseId={}, type={}.", exercise.getId(), exercise.getExerciseType());
            var config = PlagiarismDetectionConfig.createDefault();
            exercise.setPlagiarismDetectionConfig(config);
            repository.save(exercise);
        }
    }
}
