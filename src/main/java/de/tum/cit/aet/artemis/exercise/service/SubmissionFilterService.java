package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadSubmission;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

@Service
@Profile(PROFILE_CORE)
public class SubmissionFilterService {

    /**
     * Filters a set of submissions from a participation and determines the latest submission with a valid result, based
     * on the type of the exercise. This method is used to get data for the course dashboard, in which case the
     * assessment due date matters, but also to get the latest results for each participation with points for each
     * grading criterion, where the assessment due date can be ignored, because the corresponding endpoint is only
     * visible for instructors.
     *
     * @param submissions             the submissions to filter
     * @param ignoreAssessmentDueDate if the assessment due date should be ignored
     * @return an optional containing the latest submission with a valid result if it exists, otherwise an empty optional
     */
    public Optional<Submission> getLatestSubmissionWithResult(Set<Submission> submissions, boolean ignoreAssessmentDueDate) {
        if (submissions == null || submissions.isEmpty()) {
            return Optional.empty();
        }
        return submissions.stream().filter(submission -> isSubmissionRelevantForCourseDashboard(submission, ignoreAssessmentDueDate)).max(Comparator.naturalOrder());
    }

    /**
     * Determines if a submission is relevant for the course dashboard, and is safe to display there.
     *
     * @param submission the submission that is being checked.
     * @return true if the submission can be displayed, false otherwise.
     */
    private boolean isSubmissionRelevantForCourseDashboard(Submission submission, boolean ignoreAssessmentDueDate) {
        return switch (submission) {
            case ProgrammingSubmission programmingSubmission -> isProgrammingSubmissionRelevantForCourseDashboard(programmingSubmission, ignoreAssessmentDueDate);
            case ModelingSubmission modelingSubmission -> isModelingSubmissionRelevantForCourseDashboard(modelingSubmission, ignoreAssessmentDueDate);
            case TextSubmission textSubmission -> isTextSubmissionRelevantForCourseDashboard(textSubmission, ignoreAssessmentDueDate);
            case FileUploadSubmission fileUploadSubmission -> isFileUploadSubmissionRelevantForCourseDashboard(fileUploadSubmission, ignoreAssessmentDueDate);
            case QuizSubmission quizSubmission -> isQuizSubmissionRelevantForCourseDashboard(quizSubmission);
            default -> throw new IllegalArgumentException("Unsupported submission type: " + submission.getClass());
        };
    }

    private boolean isProgrammingSubmissionRelevantForCourseDashboard(ProgrammingSubmission programmingSubmission, boolean ignoreAssessmentDueDate) {
        var latestResult = programmingSubmission.getLatestResult();
        var participation = (StudentParticipation) programmingSubmission.getParticipation();
        var exercise = participation.getExercise();

        // if the result is missing from this submission, we still consider it valid for the dashboard if it was created
        // during the allowed timeframe of the exercise (or participation, if the participant has a different due date)
        if (latestResult == null) {
            return exercise.getDueDate() == null || programmingSubmission.getSubmissionDate().isBefore(exercise.getDueDateForParticipation(participation));
        }

        // if the result is not rated, we don't consider it
        if (Boolean.FALSE.equals(latestResult.isRated())) {
            return false;
        }
        // the latest rated automatic (or athena based) result can always be chosen
        if (!latestResult.isManual()) {
            return true;
        }
        // a manual result may only be shared if its assessment is complete and the assessment due date has passed
        boolean isAssessmentPeriodOverOrIgnored = ignoreAssessmentDueDate || ExerciseDateService.isAfterAssessmentDueDate(exercise);
        if (isAssessmentPeriodOverOrIgnored && latestResult.isAssessmentComplete()) {
            return true;
        }
        /*
         * The last submission of manually graded programming exercises can contain multiple results:
         * -> The first result is automatic, because that is the code that the student submitted and is evaluated by the automatic tests.
         * -> All following results are semi-automatic (and therefore manual), created by a tutor during assessment.
         * Sometimes we cannot show the last submission because the assessment due date has not yet passed,
         * but we should still show the student the last automatically determined score, i.e. the first result.
         */
        var firstResult = programmingSubmission.getFirstResult();
        if (firstResult != null && !firstResult.isManual()) {
            programmingSubmission.setResults(List.of(firstResult));
            return true;
        }
        return false;
    }

    private boolean isNonProgrammingSubmissionRelevantForCourseDashboard(Submission submission, boolean ignoreAssessmentDueDate) {
        var exercise = submission.getParticipation().getExercise();
        var result = submission.getLatestResult();
        var isAssessmentPeriodOverOrIgnored = ignoreAssessmentDueDate || ExerciseDateService.isAfterAssessmentDueDate(exercise);
        return result != null && Boolean.TRUE.equals(result.isRated()) && isAssessmentPeriodOverOrIgnored;
    }

    private boolean isModelingSubmissionRelevantForCourseDashboard(ModelingSubmission modelingSubmission, boolean ignoreAssessmentDueDate) {
        return isNonProgrammingSubmissionRelevantForCourseDashboard(modelingSubmission, ignoreAssessmentDueDate);
    }

    private boolean isTextSubmissionRelevantForCourseDashboard(TextSubmission textSubmission, boolean ignoreAssessmentDueDate) {
        return isNonProgrammingSubmissionRelevantForCourseDashboard(textSubmission, ignoreAssessmentDueDate);
    }

    private boolean isFileUploadSubmissionRelevantForCourseDashboard(FileUploadSubmission fileUploadSubmission, boolean ignoreAssessmentDueDate) {
        return isNonProgrammingSubmissionRelevantForCourseDashboard(fileUploadSubmission, ignoreAssessmentDueDate);
    }

    private boolean isQuizSubmissionRelevantForCourseDashboard(QuizSubmission quizSubmission) {
        var participation = (StudentParticipation) quizSubmission.getParticipation();
        QuizExercise exercise = (QuizExercise) participation.getExercise();
        // The shouldFilterForStudents() method uses the exercise release/due dates, not the ones of the exam,
        // therefore we can only use them if this exercise is not part of an exam
        // In exams, all results should be seen as relevant as they will only be created once the exam is over
        if (exercise.shouldFilterForStudents() && !exercise.isExamExercise()) {
            // results are never relevant before quiz has ended
            return false;
        }
        else {
            var result = quizSubmission.getLatestResult();
            if (result == null) {
                return false;
            }
            return Boolean.TRUE.equals(result.isRated()) && result.isAssessmentComplete();
        }
    }
}
