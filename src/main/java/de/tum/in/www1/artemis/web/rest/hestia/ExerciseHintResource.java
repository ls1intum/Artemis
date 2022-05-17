package de.tum.in.www1.artemis.web.rest.hestia;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.hestia.CodeHint;
import de.tum.in.www1.artemis.domain.hestia.ExerciseHint;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTask;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.hestia.ExerciseHintRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseTaskRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;
import tech.jhipster.web.util.HeaderUtil;

/**
 * REST controller for managing {@link de.tum.in.www1.artemis.domain.hestia.ExerciseHint}.
 */
@RestController
@RequestMapping("api/")
public class ExerciseHintResource {

    private final Logger log = LoggerFactory.getLogger(ExerciseHintResource.class);

    private static final String EXERCISE_HINT_ENTITY_NAME = "exerciseHint";

    private static final String CODE_HINT_ENTITY_NAME = "codeHint";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ExerciseHintRepository exerciseHintRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseTaskRepository programmingExerciseTaskRepository;

    private final AuthorizationCheckService authCheckService;

    private final ExerciseRepository exerciseRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final UserRepository userRepository;

    private static final int CODE_HINT_DISPLAY_THRESHOLD = 3;

    public ExerciseHintResource(ExerciseHintRepository exerciseHintRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            ProgrammingExerciseTaskRepository programmingExerciseTaskRepository, AuthorizationCheckService authCheckService, ExerciseRepository exerciseRepository,
            StudentParticipationRepository studentParticipationRepository, UserRepository userRepository) {
        this.exerciseHintRepository = exerciseHintRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseTaskRepository = programmingExerciseTaskRepository;
        this.authCheckService = authCheckService;
        this.exerciseRepository = exerciseRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.userRepository = userRepository;
    }

    /**
     * {@code POST  exercises/:exerciseId/exercise-hints} : Create a new exerciseHint for an exercise.
     *
     * @param exerciseHint the exerciseHint to create
     * @param exerciseId the exerciseId of the exercise of which to create the exerciseHint
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new exerciseHint,
     * or with status {@code 400 (Bad Request)} if the exerciseHint is a codeHint,
     * or with status {@code 409 (Conflict)} if the exerciseId is invalid,
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("exercises/{exerciseId}/exercise-hints")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<ExerciseHint> createExerciseHint(@RequestBody ExerciseHint exerciseHint, @PathVariable Long exerciseId) throws URISyntaxException {
        log.debug("REST request to save ExerciseHint : {}", exerciseHint);

        // Reload the exercise from the database as we can't trust data from the client
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, null);

        if (exerciseHint instanceof CodeHint) {
            throw new BadRequestAlertException("A code hint cannot be created manually.", CODE_HINT_ENTITY_NAME, "manualCodeHintOperation");
        }
        if (exerciseHint.getExercise() == null) {
            throw new ConflictException("An exercise hint can only be created if the exercise is defined.", EXERCISE_HINT_ENTITY_NAME, "exerciseNotDefined");
        }

        if (!exerciseHint.getExercise().getId().equals(exerciseId)) {
            throw new ConflictException("An exercise hint can only be created if the exerciseIds match.", EXERCISE_HINT_ENTITY_NAME, "exerciseIdMismatch");
        }

        // Hints for exam exercises are not supported at the moment
        if (exercise.isExamExercise()) {
            throw new AccessForbiddenException("Exercise hints for exams are currently not supported");
        }
        ExerciseHint result = exerciseHintRepository.save(exerciseHint);
        return ResponseEntity.created(new URI("/api/exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, EXERCISE_HINT_ENTITY_NAME, result.getId().toString())).body(result);
    }

    /**
     * {@code PUT  exercises/:exerciseId/exercise-hints/{id}} : Updates an existing exerciseHint.
     *
     * @param exerciseHint the exerciseHint to update
     * @param exerciseId the exerciseId of the exercise of which to update the exerciseHint
     * @param exerciseHintId the id to the exerciseHint
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated exerciseHint,
     * or with status {@code 400 (Bad Request)} if the exerciseHint is a codeHint,
     * or with status {@code 409 (Conflict} if the exerciseHint or exerciseId are not valid,
     * or with status {@code 500 (Internal Server Error)} if the exerciseHint couldn't be updated.
     */
    @PutMapping("exercises/{exerciseId}/exercise-hints/{exerciseHintId}")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<ExerciseHint> updateExerciseHint(@RequestBody ExerciseHint exerciseHint, @PathVariable Long exerciseHintId, @PathVariable Long exerciseId) {
        log.debug("REST request to update ExerciseHint : {}", exerciseHint);

        // Reload the exercise from the database as we can't trust data from the client
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, null);
        var hintBeforeSaving = exerciseHintRepository.findByIdElseThrow(exerciseHintId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, hintBeforeSaving.getExercise(), null);

        if (exerciseHint instanceof CodeHint) {
            throw new BadRequestAlertException("A code hint cannot be updated manually.", CODE_HINT_ENTITY_NAME, "manualCodeHintOperation");
        }

        if (exerciseHint.getId() == null || !exerciseHintId.equals(exerciseHint.getId()) || exerciseHint.getExercise() == null) {
            throw new ConflictException("An exercise hint can only be changed if it has an ID and if the exercise is not null.", EXERCISE_HINT_ENTITY_NAME, "exerciseNotDefined");
        }

        if (!exerciseHint.getExercise().getId().equals(exerciseId)) {
            throw new ConflictException("An exercise hint can only be updated if the exerciseIds match.", EXERCISE_HINT_ENTITY_NAME, "exerciseIdsMismatch");
        }

        // Hints for exam exercises are not supported at the moment
        if (exercise.isExamExercise()) {
            throw new AccessForbiddenException("Exercise hints for exams are currently not supported");
        }
        ExerciseHint result = exerciseHintRepository.save(exerciseHint);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, EXERCISE_HINT_ENTITY_NAME, exerciseHint.getId().toString())).body(result);
    }

    /**
     * GET exercises/:exerciseId/exercise-hints/:hintId/title : Returns the title of the hint with the given id
     *
     * @param exerciseHintId the id of the exerciseHint
     * @param exerciseId the exerciseId of the exercise of which to retrieve the exerciseHints' title
     * @return the title of the hint wrapped in an ResponseEntity or 404 Not Found if no hint with that id exists
     * or with status {@code 409 (Conflict)} if the exerciseId is not valid.
     */
    @GetMapping("exercises/{exerciseId}/exercise-hints/{exerciseHintId}/title")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<String> getHintTitle(@PathVariable Long exerciseId, @PathVariable Long exerciseHintId) {
        final var hint = exerciseHintRepository.findByIdElseThrow(exerciseHintId);

        if (hint.getExercise() == null || !hint.getExercise().getId().equals(exerciseId)) {
            throw new ConflictException("An exercise hint can only be retrieved if the exerciseIds match.", EXERCISE_HINT_ENTITY_NAME, "exerciseIdsMismatch");
        }

        return hint.getTitle() == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(hint.getTitle());
    }

    /**
     * {@code GET  exercises/:exerciseId/exercise-hints/:exerciseHintId} : get the exerciseHint with the given id.
     *
     * @param exerciseHintId the id of the exerciseHint to retrieve.
     * @param exerciseId the exerciseId of the exercise of which to retrieve the exerciseHint
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the exerciseHint,
     * or with status {@code 404 (Not Found)},
     * or with status {@code 409 (Conflict)} if the exerciseId is not valid.
     */
    @GetMapping("exercises/{exerciseId}/exercise-hints/{exerciseHintId}")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<ExerciseHint> getExerciseHint(@PathVariable Long exerciseId, @PathVariable Long exerciseHintId) {
        log.debug("REST request to get ExerciseHint : {}", exerciseHintId);
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        if (exercise.isExamExercise()) {
            // not allowed for exam exercises
            throw new AccessForbiddenException("");
        }

        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, null);
        var exerciseHint = exerciseHintRepository.findByIdElseThrow(exerciseHintId);

        if (!exerciseHint.getExercise().getId().equals(exerciseId)) {
            throw new ConflictException("An exercise hint can only be retrieved if the exerciseIds match.", EXERCISE_HINT_ENTITY_NAME, "exerciseIdsMismatch");
        }

        return ResponseEntity.ok().body(exerciseHint);
    }

    /**
     * {@code GET  exercises/:exerciseId/exercise-hints} : get the exerciseHints of a provided exercise.
     *
     * @param exerciseId the exercise id of which to retrieve the exercise hints.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the exerciseHint,
     * or with status {@code 404 (Not Found)},
     * or with status {@code 409 (Conflict)} if the exerciseId is not valid.
     */
    @GetMapping("exercises/{exerciseId}/exercise-hints")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Set<ExerciseHint>> getExerciseHintsForExercise(@PathVariable Long exerciseId) {
        log.debug("REST request to get ExerciseHint : {}", exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, programmingExercise, null);
        Set<ExerciseHint> exerciseHints = exerciseHintRepository.findByExerciseId(exerciseId);
        // filter out code hints if the requester is not at least editor
        boolean isAtLeastEditor = authCheckService.isAtLeastEditorForExercise(programmingExercise);
        if (!isAtLeastEditor) {
            exerciseHints = exerciseHints.stream().filter(hint -> !(hint instanceof CodeHint)).collect(Collectors.toSet());
        }
        return ResponseEntity.ok(exerciseHints);
    }

    /**
     * {@code DELETE  exercises/:exerciseId/exercise-hints/:exerciseHintId} : delete the exerciseHint with given id.
     *
     * @param exerciseHintId the id of the exerciseHint to delete
     * @param exerciseId the exercise id of which to delete the exercise hint
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)},
     * or with status {@code 400 (Bad Request)} if the exerciseHint is a codeHint,
     * or with status {@code 409 (Conflict)} if the exerciseId is not valid.
     */
    @DeleteMapping("exercises/{exerciseId}/exercise-hints/{exerciseHintId}")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<Void> deleteExerciseHint(@PathVariable Long exerciseId, @PathVariable Long exerciseHintId) {
        log.debug("REST request to delete ExerciseHint : {}", exerciseHintId);
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, null);

        var exerciseHint = exerciseHintRepository.findByIdElseThrow(exerciseHintId);
        if (exerciseHint instanceof CodeHint) {
            throw new BadRequestAlertException("A code hint cannot be deleted manually.", CODE_HINT_ENTITY_NAME, "manualCodeHintOperation");
        }

        if (!exerciseHint.getExercise().getId().equals(exerciseId)) {
            throw new ConflictException("An exercise hint can only be deleted if the exerciseIds match.", EXERCISE_HINT_ENTITY_NAME, "exerciseIdsMismatch");
        }

        exerciseHintRepository.deleteById(exerciseHintId);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, EXERCISE_HINT_ENTITY_NAME, exerciseHintId.toString())).build();
    }

    /**
     * {@code GET  programming-exercises/:exerciseId/available-exercise-hints} : get the available exercise hints.
     * Exercise hints for a task will only be shown, if the following conditions are met:
     * (1) at least {@link #CODE_HINT_DISPLAY_THRESHOLD} student submissions exist
     * (2) the result for the first unsuccessful task has not changed for at least three submissions
     * (3) if there is a previous task: the result for the previous task is successful for at least the last three results
     * Note: A task is successful, if the feedback for all associated test cases is positive
     *
     * @param exerciseId the exerciseId of the exercise of which to retrieve the codeHint
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the exercise hints
     */
    @GetMapping("exercises/{exerciseId}/available-exercise-hints")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Set<ExerciseHint>> getAvailableCodeHintsAsStudent(@PathVariable Long exerciseId) {
        log.debug("REST request to get a CodeHint for programming exercise : {}", exerciseId);
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        if (exercise.isExamExercise()) {
            // not allowed for exam exercises
            throw new AccessForbiddenException("");
        }

        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);

        var availableExerciseHints = getAvailableExerciseHints(exercise, user);
        availableExerciseHints.forEach(ExerciseHint::removeContent);

        return ResponseEntity.ok().body(availableExerciseHints);
    }

    private Set<ExerciseHint> getAvailableExerciseHints(ProgrammingExercise exercise, User user) {
        Set<ExerciseHint> availableExerciseHints = new HashSet<>();
        var exerciseHints = exerciseHintRepository.findByExerciseId(exercise.getId());
        var tasks = new ArrayList<>(programmingExerciseTaskRepository.findByExerciseIdWithTestCases(exercise.getId()));
        var latestThreeResults = getLatestNResults(exercise, user);

        if (latestThreeResults.size() >= CODE_HINT_DISPLAY_THRESHOLD) {
            var latestResult = latestThreeResults.get(0);

            for (int i = 0; i < tasks.size(); i++) {
                var task = tasks.get(i);
                Optional<ProgrammingExerciseTask> previousTask;
                if (i == 0) {
                    previousTask = Optional.empty();
                }
                else {
                    previousTask = Optional.of(tasks.get(i - 1));
                }

                // check that the current task has test cases with negative feedback
                if (getFeedbackForTaskAndResult(task, latestResult).stream().allMatch(Feedback::isPositive)) {
                    continue;
                }

                var currentTaskExerciseHints = exerciseHints.stream().filter(hint -> Objects.equals(hint.getProgrammingExerciseTask().getId(), task.getId()))
                        .collect(Collectors.toSet());
                if (!currentTaskExerciseHints.isEmpty() && checkUserHasAccessToCodeHintsForTask(task, previousTask, latestThreeResults)) {
                    availableExerciseHints = currentTaskExerciseHints;
                    break;
                }
            }
        }

        return availableExerciseHints;
    }

    private List<Result> getLatestNResults(ProgrammingExercise exercise, User student) {
        var allParticipationsForExercise = studentParticipationRepository.findByExerciseIdWithEagerSubmissionsResultAssessorFeedbacks(exercise.getId());
        var currentStudentParticipation = allParticipationsForExercise.stream().filter(participation -> participation.getParticipant().getParticipants().contains(student))
                .findFirst().orElseThrow(() -> new InternalServerErrorException("No user"));
        // (max) three results, sorted descending by completion date (where the first item is the latest)
        var numberOfSubmissionsToSkip = Math.max(currentStudentParticipation.getSubmissions().size() - CODE_HINT_DISPLAY_THRESHOLD, 0);
        return currentStudentParticipation.getSubmissions().stream().map(Submission::getResults).flatMap(Collection::stream).sorted(Comparator.comparing(Result::getCompletionDate))
                .skip(numberOfSubmissionsToSkip).toList();
    }

    private List<Feedback> getFeedbackForTaskAndResult(ProgrammingExerciseTask task, Result result) {
        var testCasesInTask = task.getTestCases();
        var feedbacks = result.getFeedbacks();
        return feedbacks.stream().filter(feedback -> testCasesInTask.stream().anyMatch(testCase -> Objects.equals(testCase.getTestName(), feedback.getText()))).toList();
    }

    private boolean checkUserHasAccessToCodeHintsForTask(ProgrammingExerciseTask task, Optional<ProgrammingExerciseTask> previousTask, List<Result> latestResults) {
        var feedbacksForTask = latestResults.stream().map(result -> getFeedbackForTaskAndResult(task, result)).toList();
        var latestFeedbackForTask = feedbacksForTask.get(0);
        // check that result for the previous task is successful for at least the last three results
        if (previousTask.isPresent()) {
            var feedbacksForPreviousTask = latestResults.stream().map(result -> getFeedbackForTaskAndResult(previousTask.get(), result)).toList();
            var previousTaskUnsuccessfulInSubmissions = feedbacksForPreviousTask.stream().anyMatch(feedbacks -> feedbacks.stream().anyMatch(Predicate.not(Feedback::isPositive)));
            if (previousTaskUnsuccessfulInSubmissions) {
                return false;
            }
        }

        // check that the results for current task did not change within the last submissions
        for (var feedback : latestFeedbackForTask) {
            var feedbacksSameTestCaseAndScore = feedbacksForTask.stream().skip(1).flatMap(Collection::stream)
                    .filter(feedback2 -> Objects.equals(feedback.getText(), feedback2.getText()) && Objects.equals(feedback2.getCredits(), feedback.getCredits())).toList();
            if (feedbacksSameTestCaseAndScore.size() != feedbacksForTask.size() - 1) {
                // the score for the last three feedbacks is not the same
                return false;
            }
        }

        return true;
    }
}
