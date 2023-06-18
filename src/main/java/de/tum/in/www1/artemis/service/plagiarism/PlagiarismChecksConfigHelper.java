package de.tum.in.www1.artemis.service.plagiarism;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.repository.JpaRepository;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismChecksConfig;

public class PlagiarismChecksConfigHelper {

    private static final Logger log = LoggerFactory.getLogger(PlagiarismChecksConfigHelper.class);

    private PlagiarismChecksConfigHelper() {
    }

    /**
     * Ads missing plagiarism checks config
     */
    public static <T extends Exercise> void createAndSaveDefaultIfNull(T exercise, JpaRepository<T, Long> repository) {
        if (exercise.getPlagiarismChecksConfig() == null) {
            log.info("Filling missing plagiarisms checks config: exerciseId={}, type={}.", exercise.getId(), exercise.getExerciseType());
            var config = PlagiarismChecksConfig.createDefault();
            exercise.setPlagiarismChecksConfig(config);
            repository.save(exercise);
        }
    }
}
