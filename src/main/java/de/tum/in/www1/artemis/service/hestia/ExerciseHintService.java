package de.tum.in.www1.artemis.service.hestia;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.hestia.CodeHint;
import de.tum.in.www1.artemis.domain.hestia.ExerciseHint;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTask;
import de.tum.in.www1.artemis.domain.hestia.UserExerciseHintActivation;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.hestia.ExerciseHintRepository;
import de.tum.in.www1.artemis.repository.hestia.UserExerciseHintActivationRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;

@Service
public class ExerciseHintService {

    private static final int CODE_HINT_DISPLAY_THRESHOLD = 3;

    private final AuthorizationCheckService authCheckService;

    private final ExerciseHintRepository exerciseHintRepository;

    private final ProgrammingExerciseTaskService programmingExerciseTaskService;

    private final StudentParticipationRepository studentParticipationRepository;

    private final UserExerciseHintActivationRepository userExerciseHintActivationRepository;

    public ExerciseHintService(AuthorizationCheckService authCheckService, ExerciseHintRepository exerciseHintRepository,
            ProgrammingExerciseTaskService programmingExerciseTaskService, StudentParticipationRepository studentParticipationRepository,
            UserExerciseHintActivationRepository userExerciseHintActivationRepository) {
        this.authCheckService = authCheckService;
        this.exerciseHintRepository = exerciseHintRepository;
        this.programmingExerciseTaskService = programmingExerciseTaskService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.userExerciseHintActivationRepository = userExerciseHintActivationRepository;
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
    public Map<Long, Long> copyExerciseHints(final Exercise template, final Exercise target) {
        final Map<Long, Long> hintIdMapping = new HashMap<>();
        // Copying non text hints is currently not supported
        target.setExerciseHints(template.getExerciseHints().stream().map(hint -> {
            ExerciseHint copiedHint;
            if (hint instanceof CodeHint) {
                copiedHint = new CodeHint();
            }
            else {
                copiedHint = new ExerciseHint();
            }

            copiedHint.setExercise(target);
            copiedHint.setDescription(hint.getDescription());
            copiedHint.setContent(hint.getContent());
            copiedHint.setTitle(hint.getTitle());
            exerciseHintRepository.save(copiedHint);
            hintIdMapping.put(hint.getId(), copiedHint.getId());
            return copiedHint;
        }).collect(Collectors.toSet()));

        return hintIdMapping;
    }

    /**
     * Sets the rating of an exercise hint for a user
     * The rating is saved in the associated UserExerciseHintActivation.
     *
     * @param exerciseHint The exercise hint to rate
     * @param user         The user that submits the rating
     * @param ratingValue  The value of the rating
     */
    public void rateExerciseHint(ExerciseHint exerciseHint, User user, Integer ratingValue) {
        if (ratingValue < 1 || ratingValue > 5) {
            throw new BadRequestAlertException("rating has to be between 1 and 5", "exerciseHint", "ratingValue.invalid", false);
        }
        var userExerciseHintActivation = userExerciseHintActivationRepository.findByExerciseHintAndUserElseThrow(exerciseHint.getId(), user.getId());
        userExerciseHintActivation.setRating(ratingValue);
        userExerciseHintActivationRepository.save(userExerciseHintActivation);
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
        if (!getAvailableExerciseHints((ProgrammingExercise) exerciseHint.getExercise(), user).contains(exerciseHint)) {
            return false;
        }

        var userExerciseHintActivation = new UserExerciseHintActivation();
        userExerciseHintActivation.setExerciseHint(exerciseHint);
        userExerciseHintActivation.setUser(user);
        userExerciseHintActivation.setActivationDate(ZonedDateTime.now());
        try {
            userExerciseHintActivationRepository.save(userExerciseHintActivation);
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }
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
        return userExerciseHintActivationRepository.findByExerciseAndUserWithExerciseHintRelations(exercise.getId(), user.getId()).stream().map(userExerciseHintActivation -> {
            var exerciseHint = userExerciseHintActivation.getExerciseHint();
            exerciseHint.setCurrentUserRating(userExerciseHintActivation.getRating());
            return exerciseHint;
        }).collect(Collectors.toSet());
    }

    /**
     * TODO: update doc
     * Calculates all exercise hints that the user can currently activate for a given programming exercise.
     * Exercise hints for a task will only be shown, if the following conditions are met:
     * (1) at least ExerciseHintService::CODE_HINT_DISPLAY_THRESHOLD student submissions exist
     * (2) the result for the first unsuccessful task has not changed for at least three submissions
     * (3) if there is a previous task: the result for the previous task is successful for at least the last three results
     * Note: A task is successful, if the feedback for all associated test cases is positive
     *
     * @param exercise The programming exercise
     * @param user     The user
     * @return All available exercise hints
     */
    public Set<ExerciseHint> getAvailableExerciseHints(ProgrammingExercise exercise, User user) {
        Set<ExerciseHint> availableExerciseHints = new HashSet<>();
        var submissions = getSubmissionsForStudent(exercise, user);

        if (submissions.isEmpty()) {
            return availableExerciseHints;
        }

        var latestResult = submissions.get(0).getLatestResult();

        // latest submissions has no result or latest result has no feedback (most commonly due to a build error)
        if (latestResult == null || latestResult.getFeedbacks().isEmpty()) {
            return availableExerciseHints;
        }

        var exerciseHints = exerciseHintRepository.findByExerciseId(exercise.getId());
        var tasks = programmingExerciseTaskService.getSortedTasks(exercise);

        var subsequentNumberOfUnsuccessfulSubmissionsByTask = tasks.stream()
                .collect(Collectors.toMap(task -> task, task -> subsequentNumberOfSuccessfulSubmissionsForTask(submissions, task, false)));
        var subsequentNumberOfSuccessfulSubmissionsByTask = tasks.stream()
                .collect(Collectors.toMap(task -> task, task -> subsequentNumberOfSuccessfulSubmissionsForTask(submissions, task, true)));

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
            if (Objects.equals(subsequentNumberSuccessfulSubmissionsForPreviousTask.orElse(-1), 0)) {
                continue;
            }

            // add the available hints for the current task
            var availableHintsForCurrentTask = getAvailableExerciseHintsForTask(subsequentNumberSuccessfulSubmissionsForPreviousTask,
                    subsequentNumberOfUnsuccessfulSubmissionsForCurrentTask, hintsInTask);
            availableExerciseHints.addAll(availableHintsForCurrentTask);
        }
        return availableExerciseHints;
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
        var allParticipationsForExercise = studentParticipationRepository.findByExerciseIdWithEagerSubmissionsResultAssessorFeedbacks(exercise.getId());
        var currentStudentParticipation = allParticipationsForExercise.stream().filter(participation -> participation.getParticipant().getParticipants().contains(student))
                .findFirst().orElseThrow(() -> new InternalServerErrorException("No user"));
        return currentStudentParticipation.getSubmissions().stream().sorted(Comparator.comparing(Submission::getSubmissionDate, Comparator.reverseOrder())).toList();
    }

    private int subsequentNumberOfSuccessfulSubmissionsForTask(List<Submission> submissions, ProgrammingExerciseTask task, boolean successful) {
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
     * @param subsequentNumberSuccessfulSubmissionsForPreviousTask the subsequent number of the latest submissions the previous task is successful
     * @param subsequentNumberOfUnsuccessfulSubmissionsForCurrentTask the subsequent number of latest submissions the current task is unsuccessful
     * @param taskHints all exercise hints in current tasks
     * @return the available exercise hints
     */
    private Set<ExerciseHint> getAvailableExerciseHintsForTask(Optional<Integer> subsequentNumberSuccessfulSubmissionsForPreviousTask,
            int subsequentNumberOfUnsuccessfulSubmissionsForCurrentTask, Set<ExerciseHint> taskHints) {
        Set<ExerciseHint> availableHintsForTask = new HashSet<>();
        for (ExerciseHint hint : taskHints) {
            // condition 1
            if (subsequentNumberSuccessfulSubmissionsForPreviousTask.isPresent() && subsequentNumberSuccessfulSubmissionsForPreviousTask.get() < hint.getThreshold()) {
                continue;
            }

            // condition 2
            if (subsequentNumberOfUnsuccessfulSubmissionsForCurrentTask >= hint.getThreshold()) {
                availableHintsForTask.add(hint);
            }
        }
        return availableHintsForTask;
    }
}
