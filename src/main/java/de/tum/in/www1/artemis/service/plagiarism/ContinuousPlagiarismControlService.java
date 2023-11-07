package de.tum.in.www1.artemis.service.plagiarism;

import static java.lang.String.format;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Predicate;

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
import de.tum.in.www1.artemis.domain.enumeration.DisplayPriority;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.plagiarism.*;
import de.tum.in.www1.artemis.exception.ArtemisMailException;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismCaseRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismComparisonRepository;
import de.tum.in.www1.artemis.service.metis.PostService;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;

/**
 * Manages continuous plagiarism control.
 */
@Service
@Component
@Profile("scheduling")
public class ContinuousPlagiarismControlService {

    private static final Logger log = LoggerFactory.getLogger(ContinuousPlagiarismControlService.class);

    private static final Predicate<Exercise> isBeforeDueDateOrAfterWithPostDueDateChecksEnabled = exercise -> exercise.getDueDate() == null
            || exercise.getDueDate().isAfter(ZonedDateTime.now()) || exercise.getPlagiarismDetectionConfig().isContinuousPlagiarismControlPostDueDateChecksEnabled();

    private final ExerciseRepository exerciseRepository;

    private final PlagiarismDetectionService plagiarismDetectionService;

    private final PlagiarismComparisonRepository plagiarismComparisonRepository;

    private final PlagiarismCaseService plagiarismCaseService;

    private final PlagiarismCaseRepository plagiarismCaseRepository;

    private final PostService postService;

    public ContinuousPlagiarismControlService(ExerciseRepository exerciseRepository, PlagiarismDetectionService plagiarismDetectionService,
            PlagiarismComparisonRepository plagiarismComparisonRepository, PlagiarismCaseService plagiarismCaseService, PlagiarismCaseRepository plagiarismCaseRepository,
            PostService postService) {
        this.exerciseRepository = exerciseRepository;
        this.plagiarismDetectionService = plagiarismDetectionService;
        this.plagiarismComparisonRepository = plagiarismComparisonRepository;
        this.plagiarismCaseService = plagiarismCaseService;
        this.plagiarismCaseRepository = plagiarismCaseRepository;
        this.postService = postService;
    }

    /**
     * Daily triggers plagiarism checks as a part of continuous plagiarism control.
     */
    @Scheduled(cron = "${artemis.scheduling.continuous-plagiarism-control-trigger-time:0 0 5 * * *}")
    public void executeChecks() {
        log.info("Starting continuous plagiarism control...");

        var exercises = exerciseRepository.findAllExercisesWithDueDateOnOrAfterYesterdayAndContinuousPlagiarismControlEnabledIsTrue();
        exercises.stream().filter(isBeforeDueDateOrAfterWithPostDueDateChecksEnabled).forEach(exercise -> {
            log.info("Started continuous plagiarism control for exercise: exerciseId={}, type={}.", exercise.getId(), exercise.getExerciseType());
            final long startTime = System.nanoTime();

            PlagiarismDetectionConfigHelper.createAndSaveDefaultIfNullAndCourseExercise(exercise, exerciseRepository);

            try {
                var result = executeChecksForExercise(exercise);
                updatePlagiarismCases(result, exercise);
            }
            catch (ExitException e) {
                log.error("Cannot check plagiarism due to Jplag error: exerciseId={}, type={}, error={}.", exercise.getId(), exercise.getExerciseType(), e.getMessage(), e);
            }
            catch (Exception e) {
                // Catch all exception to keep cpc going
                log.error("Cannot check plagiarism due to unknown error: exerciseId={}, type={}, error={}.", exercise.getId(), exercise.getExerciseType(), e.getMessage(), e);
            }

            log.info("Finished continuous plagiarism control for exercise: exerciseId={}, elapsed={}.", exercise.getId(), TimeLogUtil.formatDurationFrom(startTime));
        });

        log.debug("Continuous plagiarism control done.");
    }

    private PlagiarismResult<?> executeChecksForExercise(Exercise exercise) throws Exception {
        return switch (exercise.getExerciseType()) {
            case TEXT -> plagiarismDetectionService.checkTextExercise((TextExercise) exercise);
            case PROGRAMMING -> plagiarismDetectionService.checkProgrammingExercise((ProgrammingExercise) exercise);
            case MODELING -> plagiarismDetectionService.checkModelingExercise((ModelingExercise) exercise);
            case FILE_UPLOAD, MATH, QUIZ -> throw new IllegalStateException(
                    format("Cannot check plagiarism for exercise: type=%s, id=%s.", exercise.getExerciseType(), exercise.getId()));
        };
    }

    private void updatePlagiarismCases(PlagiarismResult<?> result, Exercise exercise) {
        addCurrentComparisonsToPlagiarismCases(result);
        removeStalePlagiarismCases(exercise.getId());
    }

    private <E extends PlagiarismSubmissionElement> void addCurrentComparisonsToPlagiarismCases(PlagiarismResult<E> result) {
        result.getComparisons().forEach(comparison -> {
            comparison.setPlagiarismResult(result);
            plagiarismComparisonRepository.updatePlagiarismComparisonStatus(comparison.getId(), PlagiarismStatus.CONFIRMED);
            createOrUpdatePlagiarismCases(comparison);
        });
    }

    private void createOrUpdatePlagiarismCases(PlagiarismComparison<?> comparison) {
        var plagiarismCases = Set.of(plagiarismCaseService.createOrAddToPlagiarismCaseForStudent(comparison, comparison.getSubmissionA(), true),
                plagiarismCaseService.createOrAddToPlagiarismCaseForStudent(comparison, comparison.getSubmissionB(), true));

        plagiarismCases.stream().filter(plagiarismCase -> plagiarismCase.getPost() == null).map(ContinuousPlagiarismControlService::buildCpcPost).forEach(post -> {
            try {
                postService.createContinuousPlagiarismControlPlagiarismCasePost(post);
            }
            catch (ArtemisMailException e) {
                // Catch mail exceptions to so that notification for the second student will be delivered
                log.error("Cannot send a cpc email: postId={}, plagiarismCaseId={}.", post.getId(), post.getPlagiarismCase().getId());
            }
        });
    }

    private static Post buildCpcPost(PlagiarismCase plagiarismCase) {
        var post = new Post();
        post.setVisibleForStudents(true);
        post.setDisplayPriority(DisplayPriority.NONE);
        post.setPlagiarismCase(plagiarismCase);
        post.setContent(ContinuousPlagiarismControlPostContentProvider.getPostContent(plagiarismCase));
        post.setCreationDate(ZonedDateTime.now());
        return post;
    }

    private void removeStalePlagiarismCases(long exerciseId) {
        var currentPlagiarismCases = plagiarismCaseRepository.findAllCreatedByContinuousPlagiarismControlByExerciseIdWithPlagiarismSubmissions(exerciseId);
        currentPlagiarismCases.stream().filter(plagiarismCase -> plagiarismCase.getPlagiarismSubmissions().isEmpty()).forEach(plagiarismCaseRepository::delete);
    }
}
