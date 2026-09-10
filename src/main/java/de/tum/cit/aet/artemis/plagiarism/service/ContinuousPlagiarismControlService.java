package de.tum.cit.aet.artemis.plagiarism.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.jplag.exceptions.ExitException;
import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.communication.domain.DisplayPriority;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.UserRole;
import de.tum.cit.aet.artemis.core.domain.FeatureKind;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsageCollector;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.plagiarism.config.PlagiarismEnabled;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismComparison;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfigHelper;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismResult;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismStatus;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismSubmissionElement;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismCaseRepository;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismComparisonRepository;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismResultRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Manages continuous plagiarism control.
 */
@Lazy
@Service
@Profile(PROFILE_SCHEDULING)
@Conditional(PlagiarismEnabled.class)
public class ContinuousPlagiarismControlService {

    private static final Logger log = LoggerFactory.getLogger(ContinuousPlagiarismControlService.class);

    private static final String PLAGIARISM_MODULE = "plagiarism";

    private static final Predicate<Exercise> isBeforeDueDateOrAfterWithPostDueDateChecksEnabled = exercise -> exercise.getDueDate() == null
            || exercise.getDueDate().isAfter(ZonedDateTime.now()) || exercise.getPlagiarismDetectionConfig().isContinuousPlagiarismControlPostDueDateChecksEnabled();

    private final ExerciseRepository exerciseRepository;

    private final PlagiarismDetectionService plagiarismDetectionService;

    private final PlagiarismComparisonRepository plagiarismComparisonRepository;

    private final PlagiarismCaseService plagiarismCaseService;

    private final PlagiarismCaseRepository plagiarismCaseRepository;

    private final PlagiarismPostService plagiarismPostService;

    private final PlagiarismResultRepository plagiarismResultRepository;

    private final UserRepository userRepository;

    /**
     * Optional because this service only needs the scheduling profile, which can in principle run without core.
     */
    private final Optional<FeatureUsageCollector> featureUsageCollector;

    public ContinuousPlagiarismControlService(ExerciseRepository exerciseRepository, PlagiarismDetectionService plagiarismDetectionService,
            PlagiarismComparisonRepository plagiarismComparisonRepository, PlagiarismCaseService plagiarismCaseService, PlagiarismCaseRepository plagiarismCaseRepository,
            PlagiarismPostService plagiarismPostService, PlagiarismResultRepository plagiarismResultRepository, UserRepository userRepository,
            Optional<FeatureUsageCollector> featureUsageCollector) {
        this.exerciseRepository = exerciseRepository;
        this.plagiarismDetectionService = plagiarismDetectionService;
        this.plagiarismComparisonRepository = plagiarismComparisonRepository;
        this.plagiarismCaseService = plagiarismCaseService;
        this.plagiarismCaseRepository = plagiarismCaseRepository;
        this.plagiarismPostService = plagiarismPostService;
        this.plagiarismResultRepository = plagiarismResultRepository;
        this.userRepository = userRepository;
        this.featureUsageCollector = featureUsageCollector;
    }

    /**
     * Daily triggers plagiarism checks as a part of continuous plagiarism control.
     */
    @Scheduled(cron = "${artemis.scheduling.continuous-plagiarism-control-trigger-time:0 0 5 * * *}")
    public void executeChecks() {
        var exercises = exerciseRepository.findAllExercisesWithDueDateOnOrAfterYesterdayAndContinuousPlagiarismControlEnabledIsTrue();
        log.info("Starting scheduled continuous plagiarism control for {} exercises: {}", exercises.size(), exercises.stream().map(Exercise::getId).toList());
        exercises.stream().filter(isBeforeDueDateOrAfterWithPostDueDateChecksEnabled).forEach(exercise -> {
            // A check whose findings nobody can act on and whose plagiarism case nobody can be named as the sender of
            // is not worth running, so a course without an instructor is skipped before the expensive part starts.
            var author = findPostAuthor(exercise);
            if (author.isEmpty()) {
                log.warn("Skipping continuous plagiarism control, the course has no instructor to act on the findings: exerciseId={}, type={}.", exercise.getId(),
                        exercise.getExerciseType());
                return;
            }

            log.info("Started continuous plagiarism control for exercise: exerciseId={}, type={}.", exercise.getId(), exercise.getExerciseType());
            final long startTime = System.nanoTime();

            PlagiarismDetectionConfigHelper.createAndSaveDefaultIfNullAndCourseExercise(exercise, exerciseRepository);

            var outcome = executeChecksForExerciseSilencingExceptions(exercise);
            updatePlagiarismCases(outcome.result(), exercise, author.get());

            log.info("Finished continuous plagiarism control for exercise: exerciseId={}, elapsed={}.", exercise.getId(), TimeLogUtil.formatDurationFrom(startTime));

            // Nothing else makes this job visible: it is purely scheduled, so without this the admin page cannot tell a
            // deployment that relies on continuous plagiarism control from one where nobody ever switched it on. A check
            // that threw is reported as a failure even though the exception was silenced to keep the run going, because
            // otherwise the error rate of this feature would be zero by construction and a broken JPlag setup would show
            // up as healthy usage.
            recordUsage(exercise, (System.nanoTime() - startTime) / 1_000_000, outcome.failed());
        });

        log.debug("Continuous plagiarism control done.");
    }

    private void recordUsage(Exercise exercise, long durationMs, boolean failed) {
        featureUsageCollector.ifPresent(collector -> collector.recordUsage(FeatureKind.BACKGROUND, PLAGIARISM_MODULE,
                "continuous-plagiarism-control/" + exercise.getExerciseType().name().toLowerCase(Locale.ROOT), Role.ANONYMOUS, failed, durationMs));
    }

    /**
     * Performs plagiarism checks on the given exercise.
     * In case any exception is thrown, the method catches it, logs it and removes any plagiarism results associated with the exercise.
     *
     * @param exercise the exercise to perform plagiarism checks on
     * @return the result of the checks and whether they failed. The absence of a result is deliberately not the same thing
     *         as a failure: modeling, file upload and quiz exercises have no plagiarism check at all and legitimately
     *         produce none, so deriving the failure from a null result would report every one of them as broken.
     */
    private CheckOutcome executeChecksForExerciseSilencingExceptions(Exercise exercise) {
        try {
            return new CheckOutcome(executeChecksForExercise(exercise), false);
        }
        catch (Exception e) {
            // Catch all exception to keep cpc going for other exercises
            if (e instanceof ExitException) {
                log.error("Cannot check plagiarism due to a Jplag error: exerciseId={}, type={}, error={}.", exercise.getId(), exercise.getExerciseType(), e.getMessage(), e);

            }
            else {
                log.error("Cannot check plagiarism due to an unknown error: exerciseId={}, type={}, error={}.", exercise.getId(), exercise.getExerciseType(), e.getMessage(), e);
            }

            // Clean up partial or stale plagiarism results
            plagiarismResultRepository.deletePlagiarismResultsByExerciseId(exercise.getId());

            return new CheckOutcome(null, true);
        }
    }

    /**
     * What the plagiarism check of one exercise produced.
     *
     * @param result the plagiarism result, null when the check produced none or the exercise type has no check at all
     * @param failed whether the check threw and the exception was silenced to keep the run going for the other exercises
     */
    private record CheckOutcome(@Nullable PlagiarismResult result, boolean failed) {
    }

    private PlagiarismResult executeChecksForExercise(Exercise exercise) throws Exception {
        return switch (exercise.getExerciseType()) {
            case TEXT -> plagiarismDetectionService.checkTextExercise((TextExercise) exercise);
            case PROGRAMMING -> plagiarismDetectionService.checkProgrammingExercise((ProgrammingExercise) exercise);
            case MODELING, FILE_UPLOAD, QUIZ -> null;
        };
    }

    private void updatePlagiarismCases(@Nullable PlagiarismResult result, Exercise exercise, User author) {
        if (result != null) {
            addCurrentComparisonsToPlagiarismCases(result, author);
        }
        removeStalePlagiarismCases(exercise.getId());
    }

    private <E extends PlagiarismSubmissionElement> void addCurrentComparisonsToPlagiarismCases(PlagiarismResult result, User author) {
        result.getComparisons().forEach(comparison -> {
            comparison.setPlagiarismResult(result);
            plagiarismComparisonRepository.updatePlagiarismComparisonStatus(comparison.getId(), PlagiarismStatus.CONFIRMED);
            createOrUpdatePlagiarismCases(comparison, author);
        });
    }

    private void createOrUpdatePlagiarismCases(PlagiarismComparison comparison, User author) {
        var plagiarismCases = Set.of(plagiarismCaseService.createOrAddToPlagiarismCaseForStudent(comparison, comparison.getSubmissionA(), true),
                plagiarismCaseService.createOrAddToPlagiarismCaseForStudent(comparison, comparison.getSubmissionB(), true));

        plagiarismCases.stream().filter(plagiarismCase -> plagiarismCase.getPost() == null && plagiarismCase.getStudent() != null)
                .map(plagiarismCase -> buildCpcPost(plagiarismCase, author)).forEach(post -> {
                    try {
                        plagiarismPostService.createContinuousPlagiarismControlPlagiarismCasePost(post);
                    }
                    catch (Exception e) {
                        // Catch mail exceptions to so that notification for the second student will be delivered
                        log.error("Cannot send a cpc email: postId={}, plagiarismCaseId={}.", post.getId(), post.getPlagiarismCase().getId());
                    }
                });
    }

    /**
     * Determines who the plagiarism case post is written by.
     * <p>
     * The control runs on a schedule, so there is no requesting user the post could belong to, and it used to be stored
     * without an author at all. A plagiarism case is the course's business and the student who reads the post should be
     * able to see who stands behind it and reply to somebody, so it is written in the name of a course instructor. The
     * instructor with the lowest id is taken so that repeated runs keep attributing the posts the same way.
     *
     * @param exercise the exercise the plagiarism check is about to run on
     * @return the instructor to write the post in the name of, empty if the course has none and the check is skipped
     */
    private Optional<User> findPostAuthor(Exercise exercise) {
        return userRepository.getInstructors(exercise.getCourseViaExerciseGroupOrCourseMember()).stream().min(Comparator.comparing(User::getId));
    }

    private static Post buildCpcPost(PlagiarismCase plagiarismCase, User author) {
        var post = new Post();
        post.setVisibleForStudents(true);
        post.setDisplayPriority(DisplayPriority.NONE);
        post.setPlagiarismCase(plagiarismCase);
        post.setAuthor(author);
        // The author was picked from the instructors of the course, so the role follows from how it was chosen
        post.setAuthorRole(UserRole.INSTRUCTOR);
        post.setContent(ContinuousPlagiarismControlPostContentProvider.getPostContent(plagiarismCase));
        post.setCreationDate(ZonedDateTime.now());
        return post;
    }

    private void removeStalePlagiarismCases(long exerciseId) {
        var currentPlagiarismCases = plagiarismCaseRepository.findAllCreatedByContinuousPlagiarismControlByExerciseIdWithPlagiarismSubmissions(exerciseId);
        currentPlagiarismCases.stream().filter(plagiarismCase -> plagiarismCase.getPlagiarismSubmissions().isEmpty()).forEach(plagiarismCaseRepository::delete);
    }
}
