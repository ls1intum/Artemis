import { Result } from 'app/entities/result.model';
import { cloneDeep } from 'lodash-es';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { MIN_SCORE_GREEN, MIN_SCORE_ORANGE } from 'app/app.constants';
import { isProgrammingExerciseStudentParticipation, isResultPreliminary } from 'app/exercises/programming/shared/utils/programming-exercise.utils';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Submission, SubmissionExerciseType } from 'app/entities/submission.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faCheckCircle, faQuestionCircle, faTimesCircle } from '@fortawesome/free-regular-svg-icons';
import { isModelingOrTextOrFileUpload, isParticipationInDueTime, isProgrammingOrQuiz } from 'app/exercises/shared/participation/participation.utils';
import { getExerciseDueDate } from 'app/exercises/shared/exercise/exercise.utils';
import { Exercise } from 'app/entities/exercise.model';
import { Participation } from 'app/entities/participation/participation.model';
import dayjs from 'dayjs/esm';

/**
 * Enumeration object representing the possible options that
 * the status of the result's template can be in.
 */
export enum ResultTemplateStatus {
    /**
     * An automatic result is currently being generated and should be available soon.
     * This is currently only relevant for programming exercises.
     */
    IS_BUILDING = 'IS_BUILDING',
    /**
     * A regular, finished result is available.
     * Can be rated (counts toward the score) or not rated (after the deadline for practice).
     */
    HAS_RESULT = 'HAS_RESULT',
    /**
     * There is no result or submission status that could be shown, e.g. because the student just started with the exercise.
     */
    NO_RESULT = 'NO_RESULT',
    /**
     * Submitted and the student can still continue to submit.
     */
    SUBMITTED = 'SUBMITTED',
    /**
     * Submitted and the student can no longer submit, but a result is not yet available.
     */
    SUBMITTED_WAITING_FOR_GRADING = 'SUBMITTED_WAITING_FOR_GRADING',
    /**
     * The student started the exercise but submitted too late.
     * Feedback is not yet available, and a future result will not count toward the score.
     */
    LATE_NO_FEEDBACK = 'LATE_NO_FEEDBACK',
    /**
     * The student started the exercise and submitted too late, but feedback is available.
     */
    LATE = 'LATE',
    /**
     * No latest result available, e.g. because building took too long and the webapp did not receive it in time.
     * This is a distinct state because we want the student to know about this problematic state
     * and not confuse them by showing a previous result that does not match the latest submission.
     */
    MISSING = 'MISSING',
}

/**
 * Information about a missing result to communicate problems and give hints how to respond.
 */
export enum MissingResultInformation {
    NONE = 'NONE',
    FAILED_PROGRAMMING_SUBMISSION_ONLINE_IDE = 'FAILED_PROGRAMMING_SUBMISSION_ONLINE_IDE',
    FAILED_PROGRAMMING_SUBMISSION_OFFLINE_IDE = 'FAILED_PROGRAMMING_SUBMISSION_OFFLINE_IDE',
}

/**
 * Check if the given result was initialized and has a score
 *
 * @param result
 */
export const initializedResultWithScore = (result?: Result) => {
    return result?.score !== undefined;
};

/**
 * Prepare a result that contains a participation which is needed in the rating component
 */
export const addParticipationToResult = (result: Result | undefined, participation: StudentParticipation) => {
    const ratingResult = cloneDeep(result);
    if (ratingResult) {
        const ratingParticipation = cloneDeep(participation);
        // remove circular dependency
        ratingParticipation.exercise!.studentParticipations = [];
        ratingResult.participation = ratingParticipation;
    }
    return ratingResult;
};

/**
 * searches for all unreferenced feedback in an array of feedbacks of a result
 * @param feedbacks the feedback of a result
 * @returns an array with the unreferenced feedback of the result
 */
export const getUnreferencedFeedback = (feedbacks: Feedback[] | undefined): Feedback[] | undefined => {
    return feedbacks ? feedbacks.filter((feedbackElement) => !feedbackElement.reference && feedbackElement.type === FeedbackType.MANUAL_UNREFERENCED) : undefined;
};

export const evaluateTemplateStatus = (
    exercise: Exercise | undefined,
    participation: Participation | undefined,
    result: Result | undefined,
    isBuilding: boolean,
    missingResultInfo = MissingResultInformation.NONE,
) => {
    // Fallback if participation is not set
    if (!participation || !exercise) {
        if (!result) {
            return ResultTemplateStatus.NO_RESULT;
        } else {
            return ResultTemplateStatus.HAS_RESULT;
        }
    }

    // If there is a problem, it has priority, and we show that instead
    if (missingResultInfo !== MissingResultInformation.NONE) {
        return ResultTemplateStatus.MISSING;
    }

    // Evaluate status for modeling, text and file-upload exercises
    if (isModelingOrTextOrFileUpload(participation)) {
        // Based on its submission we test if the participation is in due time of the given exercise.

        const inDueTime = isParticipationInDueTime(participation, exercise);
        const dueDate = getExerciseDueDate(exercise, participation);
        const assessmentDueDate = exercise.assessmentDueDate;

        if (inDueTime && initializedResultWithScore(result)) {
            // Submission is in due time of exercise and has a result with score
            if (!assessmentDueDate || assessmentDueDate.isBefore(dayjs())) {
                // the assessment due date has passed (or there was none)
                return ResultTemplateStatus.HAS_RESULT;
            } else {
                // the assessment period is still active
                return ResultTemplateStatus.SUBMITTED_WAITING_FOR_GRADING;
            }
        } else if (inDueTime && !initializedResultWithScore(result)) {
            // Submission is in due time of exercise and doesn't have a result with score.
            if (!dueDate || dueDate.isSameOrAfter(dayjs())) {
                // the due date is in the future (or there is none) => the exercise is still ongoing
                return ResultTemplateStatus.SUBMITTED;
            } else if (!assessmentDueDate || assessmentDueDate.isSameOrAfter(dayjs())) {
                // the due date is over, further submissions are no longer possible, waiting for grading
                return ResultTemplateStatus.SUBMITTED_WAITING_FOR_GRADING;
            } else {
                // the due date is over, further submissions are no longer possible, no result after assessment due date
                // TODO why is this distinct from the case above? The submission can still be graded and often is.
                return ResultTemplateStatus.NO_RESULT;
            }
        } else if (initializedResultWithScore(result) && (!assessmentDueDate || assessmentDueDate.isBefore(dayjs()))) {
            // Submission is not in due time of exercise, has a result with score and there is no assessmentDueDate for the exercise or it lies in the past.
            // TODO handle external submissions with new status "External"
            return ResultTemplateStatus.LATE;
        } else {
            // Submission is not in due time of exercise and there is actually no feedback for the submission or the feedback should not be displayed yet.
            return ResultTemplateStatus.LATE_NO_FEEDBACK;
        }
    }

    // Evaluate status for programming and quiz exercises
    if (isProgrammingOrQuiz(participation)) {
        if (isBuilding) {
            return ResultTemplateStatus.IS_BUILDING;
        } else if (initializedResultWithScore(result)) {
            return ResultTemplateStatus.HAS_RESULT;
        } else {
            return ResultTemplateStatus.NO_RESULT;
        }
    }

    return ResultTemplateStatus.NO_RESULT;
};

/**
 * Checks if only compilation was tested. This is the case, when a successful result is present with 0 of 0 passed tests
 *
 */
export const onlyShowSuccessfulCompileStatus = (result: Result | undefined, templateStatus: ResultTemplateStatus): boolean => {
    const zeroTests = !result?.testCaseCount;
    return templateStatus !== ResultTemplateStatus.NO_RESULT && templateStatus !== ResultTemplateStatus.IS_BUILDING && !isBuildFailed(result?.submission) && zeroTests;
};

/**
 * Get the css class for the entire text as a string
 *
 * @return {string} the css class
 */
export const getTextColorClass = (result: Result | undefined, templateStatus: ResultTemplateStatus) => {
    if (!result) {
        return 'text-secondary';
    }

    if (templateStatus === ResultTemplateStatus.LATE) {
        return 'result-late';
    }

    if (isBuildFailedAndResultIsAutomatic(result)) {
        return 'text-danger';
    }

    if (resultIsPreliminary(result)) {
        return 'text-secondary';
    }

    if (result?.score === undefined) {
        return result?.successful ? 'text-success' : 'text-danger';
    }

    if (result.score >= MIN_SCORE_GREEN) {
        return 'text-success';
    }

    if (result.score >= MIN_SCORE_ORANGE) {
        return 'result-orange';
    }

    return 'text-danger';
};

/**
 * Get the icon type for the result icon as an array
 *
 */
export const getResultIconClass = (result: Result | undefined, templateStatus: ResultTemplateStatus): IconProp => {
    if (!result) {
        return faQuestionCircle;
    }

    if (isBuildFailedAndResultIsAutomatic(result)) {
        return faTimesCircle;
    }

    if (resultIsPreliminary(result)) {
        return faQuestionCircle;
    }

    if (onlyShowSuccessfulCompileStatus(result, templateStatus)) {
        return faCheckCircle;
    }

    if (result?.score == undefined) {
        return result?.successful ? faCheckCircle : faTimesCircle;
    }
    if (result.score >= MIN_SCORE_GREEN) {
        return faCheckCircle;
    }
    return faTimesCircle;
};

/**
 * Returns true if the specified result is preliminary.
 * @param result the result. It must include a participation and exercise.
 */
export const resultIsPreliminary = (result: Result) => {
    return (
        result.participation && isProgrammingExerciseStudentParticipation(result.participation) && isResultPreliminary(result, result.participation.exercise as ProgrammingExercise)
    );
};

/**
 * Returns true if the submission of the result is of type programming, is automatic, and
 * its build has failed.
 * @param result
 */
export const isBuildFailedAndResultIsAutomatic = (result: Result) => {
    return isBuildFailed(result.submission) && !isManualResult(result);
};

/**
 * Returns true if the specified submission is a programming submissions that has a failed
 * build.
 * @param submission the submission
 */
export const isBuildFailed = (submission?: Submission) => {
    const isProgrammingSubmission = submission && submission.submissionExerciseType === SubmissionExerciseType.PROGRAMMING;
    return isProgrammingSubmission && (submission as ProgrammingSubmission).buildFailed;
};

/**
 * Returns true if the specified result is not automatic.
 * @param result the result.
 */
export const isManualResult = (result?: Result) => {
    return result?.assessmentType !== AssessmentType.AUTOMATIC;
};
