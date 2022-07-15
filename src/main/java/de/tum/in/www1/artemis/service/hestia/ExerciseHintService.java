package de.tum.in.www1.artemis.service.hestia;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.hestia.ExerciseHint;
import de.tum.in.www1.artemis.domain.hestia.ExerciseHintActivation;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTask;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.hestia.ExerciseHintActivationRepository;
import de.tum.in.www1.artemis.repository.hestia.ExerciseHintRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;

@Service
public class ExerciseHintService {

    private final AuthorizationCheckService authCheckService;

    private final ExerciseHintRepository exerciseHintRepository;

    private final ProgrammingExerciseTaskService programmingExerciseTaskService;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ExerciseHintActivationRepository exerciseHintActivationRepository;

    public ExerciseHintService(AuthorizationCheckService authCheckService, ExerciseHintRepository exerciseHintRepository,
            ProgrammingExerciseTaskService programmingExerciseTaskService, StudentParticipationRepository studentParticipationRepository,
            ExerciseHintActivationRepository exerciseHintActivationRepository) {
        this.authCheckService = authCheckService;
        this.exerciseHintRepository = exerciseHintRepository;
        this.programmingExerciseTaskService = programmingExerciseTaskService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.exerciseHintActivationRepository = exerciseHintActivationRepository;
    }

    /**
     * Copies the hints of an exercise to a new target exercise by cloning the hint objects and saving them
     * resulting in new IDs for the copied hints. The contents stay the same. On top of that, all hints in the
     * problem statement of the target exercise get replaced by the new IDs.
     *
     * @param template The template exercise containing the hints that should be copied
     * @param target   The new target exercise, to which all hints should get copied to.
     * @return A map with the old hint id as a key and the new hint id as a value
     */
    public Map<Long, Long> copyExerciseHints(final ProgrammingExercise template, final ProgrammingExercise target) {
        final Map<Long, Long> hintIdMapping = new HashMap<>();
        target.setExerciseHints(template.getExerciseHints().stream().map(hint -> {
            ExerciseHint copiedHint = hint.createCopy();
            copiedHint.setExercise(target);
            exerciseHintRepository.save(copiedHint);
            hintIdMapping.put(hint.getId(), copiedHint.getId());
            return copiedHint;
        }).collect(Collectors.toSet()));

        return hintIdMapping;
    }

    /**
     * Sets the rating of an exercise hint for a user
     * The rating is saved in the associated ExerciseHintActivation.
     *
     * @param exerciseHint The exercise hint to rate
     * @param user         The user that submits the rating
     * @param ratingValue  The value of the rating
     */
    public void rateExerciseHint(ExerciseHint exerciseHint, User user, Integer ratingValue) {
        if (ratingValue < 1 || ratingValue > 5) {
            throw new BadRequestAlertException("rating has to be between 1 and 5", "exerciseHint", "ratingValue.invalid", false);
        }
        var exerciseHintActivation = exerciseHintActivationRepository.findByExerciseHintAndUserElseThrow(exerciseHint.getId(), user.getId());
        exerciseHintActivation.setRating(ratingValue);
        exerciseHintActivationRepository.save(exerciseHintActivation);
    }

    /**
     * Activates an ExerciseHint for a user.
     * After activation the user can view the full content of the hint without restrictions.
     * This action cannot be undone
     *
     * @param exerciseHint The exercise hint
     * @param user         The user
     * @return true if the hint was activated
     */
    public boolean activateHint(ExerciseHint exerciseHint, User user) {
        // Check if the user has access to the exercise
        // This is done here to prevent illegal activation of hints in all possible future cases
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exerciseHint.getExercise(), user);

        // Check if the user can activate the hint
        if (!getAvailableExerciseHints(exerciseHint.getExercise(), user).contains(exerciseHint)) {
            return false;
        }

        // Check if the hint was already activated
        if (exerciseHintActivationRepository.findByExerciseHintAndUser(exerciseHint.getId(), user.getId()).isPresent()) {
            return false;
        }

        var exerciseHintActivation = new ExerciseHintActivation();
        exerciseHintActivation.setExerciseHint(exerciseHint);
        exerciseHintActivation.setUser(user);
        exerciseHintActivation.setActivationDate(ZonedDateTime.now());
        exerciseHintActivationRepository.save(exerciseHintActivation);
        return true;
    }

    /**
     * Retrieves all exercise hints that a user has activated for an exercise.
     *
     * @param exercise The programming exercise
     * @param user     The user
     * @return All activated exercise hints for this user and exercise
     */
    public Set<ExerciseHint> getActivatedExerciseHints(ProgrammingExercise exercise, User user) {
        return exerciseHintActivationRepository.findByExerciseAndUserWithExerciseHintRelations(exercise.getId(), user.getId()).stream().map(exerciseHintActivation -> {
            var exerciseHint = exerciseHintActivation.getExerciseHint();
            exerciseHint.setCurrentUserRating(exerciseHintActivation.getRating());
            return exerciseHint;
        }).collect(Collectors.toSet());
    }

    /**
     * Returns all exercise hints that the user can currently activate for a given programming exercise.
     * Exercise hints will be shown for the first task that meets the following conditions:
     * (1) the subsequent number of the latest submissions the previous task is successful is greater or equal to the hint's threshold
     * (2) the subsequent number of the latest submissions the current task is unsuccessful is greater or equal to the hint's threshold
     * If no task matches these conditions, no exercise hints will be returned
     * Note: A task is successful, if the feedback within the submission is positive for all associated test cases within this task
     *
     * @param exercise The programming exercise
     * @param user     The user
     * @return All available exercise hints
     */
    public Set<ExerciseHint> getAvailableExerciseHints(ProgrammingExercise exercise, User user) {
        var submissions = getSubmissionsForStudent(exercise, user);

        if (submissions.isEmpty()) {
            return new HashSet<>();
        }

        var latestResult = submissions.get(0).getLatestResult();

        // latest submissions has no result or latest result has no feedback (most commonly due to a build error)
        if (latestResult == null || latestResult.getFeedbacks().isEmpty()) {
            return new HashSet<>();
        }

        var exerciseHints = exerciseHintRepository.findByExerciseId(exercise.getId());
        var tasks = programmingExerciseTaskService.getSortedTasks(exercise);

        var subsequentNumberOfUnsuccessfulSubmissionsByTask = tasks.stream()
                .collect(Collectors.toMap(task -> task, task -> subsequentNumberOfSubmissionsForTaskWithStatus(submissions, task, false)));
        var subsequentNumberOfSuccessfulSubmissionsByTask = tasks.stream()
                .collect(Collectors.toMap(task -> task, task -> subsequentNumberOfSubmissionsForTaskWithStatus(submissions, task, true)));

        var availableHints = new HashSet<ExerciseHint>();

        for (int i = 0; i < tasks.size(); i++) {
            var task = tasks.get(i);
            int subsequentNumberOfUnsuccessfulSubmissionsForCurrentTask = subsequentNumberOfUnsuccessfulSubmissionsByTask.get(task);
            // current task is successful
            if (subsequentNumberOfUnsuccessfulSubmissionsForCurrentTask == 0) {
                continue;
            }

            var hintsInTask = exerciseHints.stream()
                    .filter(hint -> Objects.nonNull(hint.getProgrammingExerciseTask()) && Objects.equals(hint.getProgrammingExerciseTask().getId(), task.getId()))
                    .collect(Collectors.toSet());
            // no hints exist for the current task
            if (hintsInTask.isEmpty()) {
                continue;
            }

            Optional<Integer> subsequentNumberSuccessfulSubmissionsForPreviousTask;
            if (i == 0) {
                subsequentNumberSuccessfulSubmissionsForPreviousTask = Optional.empty();
            }
            else {
                subsequentNumberSuccessfulSubmissionsForPreviousTask = Optional.of(subsequentNumberOfSuccessfulSubmissionsByTask.get(tasks.get(i - 1)));
            }

            // skip current task if the previous task was not successful
            if (0 == subsequentNumberSuccessfulSubmissionsForPreviousTask.orElse(-1)) {
                continue;
            }

            // add the available hints for the current task
            var availableHintsForCurrentTask = getAvailableExerciseHintsForTask(subsequentNumberSuccessfulSubmissionsForPreviousTask,
                    subsequentNumberOfUnsuccessfulSubmissionsForCurrentTask, hintsInTask);
            if (!availableHintsForCurrentTask.isEmpty()) {
                availableHints.addAll(availableHintsForCurrentTask);
                break;
            }
        }
        // Hints with a threshold of 0 will always be displayed
        availableHints.addAll(exerciseHints.stream().filter(hint -> hint.getDisplayThreshold() == 0).toList());
        return availableHints;
    }

    private boolean isTaskSuccessfulInSubmission(ProgrammingExerciseTask task, Submission submission) {
        var result = submission.getLatestResult();
        if (result == null || result.getFeedbacks().isEmpty()) {
            return false;
        }
        var testCasesInTask = task.getTestCases();
        var feedbacks = result.getFeedbacks();
        return feedbacks.stream().filter(feedback -> testCasesInTask.stream().anyMatch(testCase -> Objects.equals(testCase.getTestName(), feedback.getText())))
                .allMatch(Feedback::isPositive);
    }

    private List<Submission> getSubmissionsForStudent(ProgrammingExercise exercise, User student) {
        var studentParticipation = studentParticipationRepository.findByExerciseIdAndStudentIdWithEagerSubmissionsResultsFeedbacksElseThrow(exercise.getId(), student.getId());
        return studentParticipation.getSubmissions().stream().sorted(Comparator.comparing(Submission::getSubmissionDate, Comparator.reverseOrder())).toList();
    }

    private int subsequentNumberOfSubmissionsForTaskWithStatus(List<Submission> submissions, ProgrammingExerciseTask task, boolean successful) {
        int subsequentNumberSuccessfulSubmissionsForTask = 0;
        for (Submission submission : submissions) {
            if (isTaskSuccessfulInSubmission(task, submission) != successful) {
                break;
            }
            subsequentNumberSuccessfulSubmissionsForTask++;
        }
        return subsequentNumberSuccessfulSubmissionsForTask;
    }

    /**
     * Filter hints that meet the following conditions:
     * 1. the previous task (if existing) is successful for at least the hint's threshold value
     * 2. the current task is unsuccessful for at least the hint's threshold value
     *
     * @param subsequentNumberSuccessfulSubmissionsForPreviousTask    the subsequent number of the latest submissions the previous task is successful
     * @param subsequentNumberOfUnsuccessfulSubmissionsForCurrentTask the subsequent number of the latest submissions the current task is unsuccessful
     * @param taskHints                                               all exercise hints in current tasks
     * @return the available exercise hints
     */
    private Set<ExerciseHint> getAvailableExerciseHintsForTask(Optional<Integer> subsequentNumberSuccessfulSubmissionsForPreviousTask,
            int subsequentNumberOfUnsuccessfulSubmissionsForCurrentTask, Set<ExerciseHint> taskHints) {
        Set<ExerciseHint> availableHintsForTask = new HashSet<>();
        for (ExerciseHint hint : taskHints) {
            // condition 1
            if (subsequentNumberSuccessfulSubmissionsForPreviousTask.isPresent() && subsequentNumberSuccessfulSubmissionsForPreviousTask.get() < hint.getDisplayThreshold()) {
                continue;
            }

            // condition 2
            if (subsequentNumberOfUnsuccessfulSubmissionsForCurrentTask >= hint.getDisplayThreshold()) {
                availableHintsForTask.add(hint);
            }
        }
        return availableHintsForTask;
    }

    /**
     * Returns the title of the hint identified by the given hint id if the exercise id stored in the hint matches the
     * provided exercise id.
     *
     * @param exerciseId the exercise id that must match the one stored in the hint
     * @param exerciseHintId the id of the hint
     * @return the title of the hint if it was found; null otherwise
     *
     * @throws ConflictException if the provided exercise id does not match the one stored in the hint
     */
    @Cacheable(cacheNames = "exerciseHintTitle", key = "''.concat(#exerciseId).concat('-').concat(#exerciseHintId)", unless = "#result == null")
    public String getExerciseHintTitle(Long exerciseId, Long exerciseHintId) {
        final var hint = exerciseHintRepository.findByIdElseThrow(exerciseHintId);
        if (hint.getExercise() == null || !hint.getExercise().getId().equals(exerciseId)) {
            throw new ConflictException("An exercise hint can only be retrieved if the exerciseIds match.", "exerciseHint", "exerciseIdsMismatch");
        }

        return hint.getTitle();
    }
}
