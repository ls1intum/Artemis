package de.tum.in.www1.artemis.service.plagiarism;

import static java.lang.String.format;

import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import de.jplag.exceptions.ExitException;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseType;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismResult;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;

/**
 * Manages continuous plagiarism control.
 */
@Service
@Component
@Profile("scheduling")
public class ContinuousPlagiarismControlService {

    private static final Logger log = LoggerFactory.getLogger(ContinuousPlagiarismControlService.class);

    private final ExerciseRepository exerciseRepository;

    private final PlagiarismChecksService plagiarismChecksService;

    private final ContinuousPlagiarismControlResultsService resultsService;

    public ContinuousPlagiarismControlService(ExerciseRepository exerciseRepository, PlagiarismChecksService plagiarismChecksService,
            ContinuousPlagiarismControlResultsService resultsService) {
        this.exerciseRepository = exerciseRepository;
        this.plagiarismChecksService = plagiarismChecksService;
        this.resultsService = resultsService;
    }

    /**
     * Daily triggers plagiarism checks as a part of continuous plagiarism control.
     */
    @Scheduled(cron = "${artemis.scheduling.continuous-plagiarism-control-trigger-time:0 0 4 * * *}")
    public void executeChecks() {
        log.info("Starting continuous plagiarism control...");

        var exercises = exerciseRepository.findAllExercisesWithCurrentOrUpcomingDueDateAndContinuousPlagiarismControlEnabledIsTrue();
        exercises.forEach(exercise -> {
            log.info("Started continuous plagiarism control for exercise: exerciseId={}, type={}.", exercise.getId(), exercise.getExerciseType());
            final long startTime = System.nanoTime();

            PlagiarismChecksConfigHelper.createAndSaveDefaultIfNull(exercise, exerciseRepository);

            try {
                var result = executeChecksForExercise(exercise);
                if (exercise.getExerciseType() != ExerciseType.PROGRAMMING) {
                    resultsService.handleCpcResult(result);
                }
            }
            catch (ExitException e) {
                log.error("Cannot check plagiarism due to Jplag error: exerciseId={}, type={}, error={}.", exercise.getId(), exercise.getExerciseType(), e.getMessage(), e);
            }
            catch (Exception e) {
                log.error("Cannot check plagiarism due to unknown error: exerciseId={}, type={}, error={}.", exercise.getId(), exercise.getExerciseType(), e.getMessage(), e);
            }

            log.info("Finished continuous plagiarism control for exercise: exerciseId={}, elapsed={}.", exercise.getId(), TimeLogUtil.formatDurationFrom(startTime));
        });

        log.debug("Continuous plagiarism control done.");
    }

    private PlagiarismResult<?> executeChecksForExercise(Exercise exercise) throws Exception {
        return switch (exercise.getExerciseType()) {
            case TEXT -> plagiarismChecksService.checkTextExercise((TextExercise) exercise);
            case PROGRAMMING -> plagiarismChecksService.checkProgrammingExercise((ProgrammingExercise) exercise);
            case MODELING -> plagiarismChecksService.checkModelingExercise((ModelingExercise) exercise);
            case FILE_UPLOAD, QUIZ -> throw new IllegalStateException(
                    format("Cannot check plagiarism for exercise: type=%s, id=%s.", exercise.getExerciseType(), exercise.getId()));
        };
    }
}
