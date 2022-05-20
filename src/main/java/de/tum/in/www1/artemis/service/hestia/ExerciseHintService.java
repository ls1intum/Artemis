package de.tum.in.www1.artemis.service.hestia;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.hestia.CodeHint;
import de.tum.in.www1.artemis.domain.hestia.ExerciseHint;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTask;
import de.tum.in.www1.artemis.domain.hestia.UserExerciseHintActivation;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.hestia.ExerciseHintRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseTaskRepository;
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

    private final StudentParticipationRepository studentParticipationRepository;

    private final ProgrammingExerciseTaskRepository programmingExerciseTaskRepository;

    private final UserExerciseHintActivationRepository userExerciseHintActivationRepository;

    public ExerciseHintService(AuthorizationCheckService authCheckService, ExerciseHintRepository exerciseHintRepository,
            StudentParticipationRepository studentParticipationRepository, ProgrammingExerciseTaskRepository programmingExerciseTaskRepository,
            UserExerciseHintActivationRepository userExerciseHintActivationRepository) {
        this.authCheckService = authCheckService;
        this.exerciseHintRepository = exerciseHintRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.programmingExerciseTaskRepository = programmingExerciseTaskRepository;
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
            copiedHint.setContent(hint.getContent());
            copiedHint.setTitle(hint.getTitle());
            exerciseHintRepository.save(copiedHint);
            hintIdMapping.put(hint.getId(), copiedHint.getId());
            return copiedHint;
        }).collect(Collectors.toSet()));

        String patchedStatement = target.getProblemStatement();
        for (final var idMapping : hintIdMapping.entrySet()) {
            // Replace any old hint ID in the imported statement with the new hint ID
            // $1 --> everything before the old hint ID; $3 --> Everything after the old hint ID --> $1 newHintID $3
            final var replacement = "$1" + idMapping.getValue() + "$3";
            patchedStatement = patchedStatement.replaceAll("(\\{[^}]*)(" + idMapping.getKey() + ")([^}]*})", replacement);
        }
        target.setProblemStatement(patchedStatement);
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
        return userExerciseHintActivationRepository.findByExerciseAndUser(exercise.getId(), user.getId()).stream().map(UserExerciseHintActivation::getExerciseHint)
                .collect(Collectors.toSet());
    }

    /**
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
        var exerciseHints = exerciseHintRepository.findByExerciseId(exercise.getId());
        var tasks = new ArrayList<>(programmingExerciseTaskRepository.findByExerciseIdWithTestCases(exercise.getId()));
        var latestNResults = getLatestNResults(exercise, user);

        if (latestNResults.size() >= CODE_HINT_DISPLAY_THRESHOLD) {
            var latestResult = latestNResults.get(0);

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
                if (!currentTaskExerciseHints.isEmpty() && checkUserHasAccessToCodeHintsForTask(task, previousTask, latestNResults)) {
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
