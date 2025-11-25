import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { MIN_SCORE_GREEN, MIN_SCORE_ORANGE } from 'app/app.constants';
import { isProgrammingExerciseStudentParticipation, isResultPreliminary } from 'app/programming/shared/utils/programming-exercise.utils';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Submission, SubmissionExerciseType } from 'app/exercise/shared/entities/submission/submission.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faCheckCircle, faQuestionCircle, faTimesCircle } from '@fortawesome/free-regular-svg-icons';
import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';
import { isModelingOrTextOrFileUpload, isParticipationInDueTime, isProgrammingOrQuiz } from 'app/exercise/participation/participation.utils';
import { getExerciseDueDate } from 'app/exercise/util/exercise.utils';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Participation, ParticipationType, getLatestSubmission } from 'app/exercise/shared/entities/participation/participation.model';
import dayjs from 'dayjs/esm';
import { ResultWithPointsPerGradingCriterion } from 'app/exercise/shared/entities/result/result-with-points-per-grading-criterion.model';
import { TestCaseResult } from 'app/programming/shared/entities/test-case-result.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';

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
     * Submission is currently queued and will be processed soon.
     * This is currently only relevant for programming exercises.
     */
    IS_QUEUED = 'IS_QUEUED',
    /**
     * An automatic feedback suggestion is currently being generated and should be available soon.
     * This is currently only relevant for programming exercises.
     */
    IS_GENERATING_FEEDBACK = 'IS_GENERATING_FEEDBACK',
    /**
     * An automatic feedback suggestion has failed.
     * This is currently only relevant for programming exercises.
     */
    FEEDBACK_GENERATION_FAILED = 'FEEDBACK_GENERATION_FAILED',
    /**
     * The generation of an automatic feedback suggestion was in progress, but did not return a result.
     * This is currently only relevant for programming exercises.
     */
    FEEDBACK_GENERATION_TIMED_OUT = 'FEEDBACK_GENERATION_TIMED_OUT',
    /**
     * A regular, finished result is available.
     * Can be rated (counts toward the score) or not rated (after the due date for practice).
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
    // TODO should not be needed anymore
    // const ratingResult = cloneDeep(result);
    // if (ratingResult) {
    //     const ratingParticipation = cloneDeep(participation);
    //     // remove circular dependency
    //     ratingParticipation.exercise!.studentParticipations = [];
    //     ratingResult.participation = ratingParticipation;
    // }
    // return ratingResult;
};

/**
 * searches for all manual unreferenced feedback in an array of feedbacks of a result
 * @param feedbacks the feedback of a result
 * @returns an array with the unreferenced feedback of the result
 */
export const getManualUnreferencedFeedback = (feedbacks: Feedback[] | undefined): Feedback[] | undefined => {
    return feedbacks ? feedbacks.filter((feedbackElement) => !feedbackElement.reference && feedbackElement.type === FeedbackType.MANUAL_UNREFERENCED) : undefined;
};

/**
 * searches for all unreferenced feedback in an array of feedbacks of a result
 * @param feedbacks the feedback of a result
 * @returns an array with the unreferenced feedback of the result
 */
export const getUnreferencedFeedback = (feedbacks: Feedback[] | undefined): Feedback[] | undefined => {
    return feedbacks
        ? feedbacks.filter(
              (feedbackElement) => !feedbackElement.reference && (feedbackElement.type === FeedbackType.MANUAL_UNREFERENCED || feedbackElement.type === FeedbackType.AUTOMATIC),
          )
        : undefined;
};

export function isAIResultAndFailed(result: Result | undefined): boolean {
    return (result && isAthenaAIResult(result) && result.successful === false) ?? false;
}

export function isAIResultAndTimedOut(result: Result | undefined): boolean {
    return (result && isAthenaAIResult(result) && result.successful === undefined && result.completionDate && dayjs().isAfter(result.completionDate)) ?? false;
}

export function isAIResultAndProcessed(result: Result | undefined): boolean {
    return (result && isAthenaAIResult(result) && result.successful === true) ?? false;
}

export function isAIResultAndIsBeingProcessed(result: Result | undefined): boolean {
    return (result && isAthenaAIResult(result) && result.successful === undefined && result.completionDate && dayjs().isSameOrBefore(result.completionDate)) ?? false;
}

/**
 * Checks whether the result is generated by Athena AI.
 *
 * @return true if the result is an automatic Athena AI result
 */
export function isAthenaAIResult(result: Result): boolean {
    return result.assessmentType === AssessmentType.AUTOMATIC_ATHENA;
}

export const evaluateTemplateStatus = (
    exercise: Exercise | undefined,
    participation: Participation | undefined,
    result: Result | undefined,
    isBuilding: boolean,
    missingResultInfo = MissingResultInformation.NONE,
    isQueued = false,
): ResultTemplateStatus => {
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
            if (!assessmentDueDate || assessmentDueDate.isBefore(dayjs()) || !isManualResult(result)) {
                // the assessment due date has passed (or there was none) (or it is not manual feedback)
                if (result?.assessmentType === AssessmentType.AUTOMATIC_ATHENA && result?.successful === undefined) {
                    return ResultTemplateStatus.IS_GENERATING_FEEDBACK;
                } else if (result?.assessmentType === AssessmentType.AUTOMATIC_ATHENA && result?.successful === false) {
                    return ResultTemplateStatus.FEEDBACK_GENERATION_FAILED;
                }
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
            } else if (!assessmentDueDate || assessmentDueDate.isSameOrAfter(dayjs()) || isManualResult(result)) {
                // the due date is over, further submissions are no longer possible, waiting for grading
                return ResultTemplateStatus.SUBMITTED_WAITING_FOR_GRADING;
            } else {
                // the due date is over, further submissions are no longer possible, no result after assessment due date
                // TODO why is this distinct from the case above? The submission can still be graded and often is.
                return ResultTemplateStatus.NO_RESULT;
            }
        } else if (initializedResultWithScore(result) && (!assessmentDueDate || assessmentDueDate.isBefore(dayjs()) || !isManualResult(result))) {
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
        if (isQueued) {
            return ResultTemplateStatus.IS_QUEUED;
        } else if (isBuilding) {
            return ResultTemplateStatus.IS_BUILDING;
        } else if (isAIResultAndIsBeingProcessed(result)) {
            return ResultTemplateStatus.IS_GENERATING_FEEDBACK;
        } else if (isAIResultAndProcessed(result)) {
            return ResultTemplateStatus.HAS_RESULT;
        } else if (isAIResultAndFailed(result)) {
            return ResultTemplateStatus.FEEDBACK_GENERATION_FAILED;
        } else if (isAIResultAndTimedOut(result)) {
            return ResultTemplateStatus.FEEDBACK_GENERATION_TIMED_OUT;
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
 * This could be because all test cases are only visible after the due date.
 */
export const isOnlyCompilationTested = (result: Result | undefined, participation: Participation, templateStatus: ResultTemplateStatus): boolean => {
    const zeroTests = !result?.testCaseCount;
    const isProgrammingExercise: boolean = participation?.exercise?.type === ExerciseType.PROGRAMMING;
    return (
        templateStatus !== ResultTemplateStatus.NO_RESULT &&
        templateStatus !== ResultTemplateStatus.IS_BUILDING &&
        // prefer the potentially newer result.submission when available (so that buildFailed is up-to-date)
        !isBuildFailed(result?.submission ?? getLatestSubmission(participation)) &&
        zeroTests &&
        isProgrammingExercise
    );
};

/**
 * Get the css class for the entire text as a string
 *
 * @return {string} the css class
 */
export const getTextColorClass = (result: Result | undefined, participation: Participation, templateStatus: ResultTemplateStatus) => {
    if (!result) {
        return 'text-secondary';
    }

    if (result.assessmentType === AssessmentType.AUTOMATIC_ATHENA) {
        if (isAIResultAndIsBeingProcessed(result)) {
            return 'text-primary';
        }
        if (isAIResultAndFailed(result)) {
            return 'text-danger';
        }
        return 'text-secondary';
    }

    if (templateStatus === ResultTemplateStatus.LATE) {
        return 'result-late';
    }

    if (isBuildFailedAndResultIsAutomatic(result, participation)) {
        return 'text-danger';
    }

    if (resultIsPreliminary(result, participation)) {
        return 'text-secondary';
    }

    if (result?.score === undefined) {
        return result?.successful ? 'text-success' : 'text-danger';
    }

    if (isOnlyCompilationTested(result, participation, templateStatus)) {
        return 'text-success';
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
export const getResultIconClass = (result: Result | undefined, participation: Participation, templateStatus: ResultTemplateStatus): IconProp => {
    if (!result) {
        return faQuestionCircle;
    }

    if (result.assessmentType === AssessmentType.AUTOMATIC_ATHENA) {
        // result loading
        if (result.successful === undefined) {
            return faCircleNotch;
        }
        // result done successfuly
        if (result.successful) {
            return faCheckCircle;
        }
        // generating failed
        return faTimesCircle;
    }

    if (isBuildFailedAndResultIsAutomatic(result, participation) || isAIResultAndFailed(result)) {
        return faTimesCircle;
    }

    if (isAIResultAndIsBeingProcessed(result)) {
        return faCircleNotch;
    }

    if (resultIsPreliminary(result, participation) || isAIResultAndTimedOut(result)) {
        return faQuestionCircle;
    }

    if (isOnlyCompilationTested(result, participation, templateStatus)) {
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
 * @param result the result.
 * @param participation the participation
 */
export const resultIsPreliminary = (result: Result, participation: Participation) => {
    const exerciseType = participation?.exercise?.type;
    if (exerciseType === ExerciseType.TEXT || exerciseType === ExerciseType.MODELING) {
        return result.assessmentType === AssessmentType.AUTOMATIC_ATHENA;
    } else return isProgrammingExerciseStudentParticipation(participation) && isResultPreliminary(result, participation, participation?.exercise as ProgrammingExercise);
};

/**
 * Returns true if the specified result is a student Participation
 * @param participation the participation
 */
export const isStudentParticipation = (participation: Participation) => {
    return Boolean(participation.type !== ParticipationType.TEMPLATE && participation.type !== ParticipationType.SOLUTION);
};

/**
 * Returns true if the submission of the result is of type programming, is automatic, and
 * its build has failed.
 * @param result
 * @param participation
 */
export const isBuildFailedAndResultIsAutomatic = (result: Result, participation: Participation) => {
    const latestSubmission = result?.submission ?? getLatestSubmission(participation);
    return isBuildFailed(latestSubmission) && !isManualResult(result);
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
    return result?.assessmentType !== AssessmentType.AUTOMATIC && result?.assessmentType !== AssessmentType.AUTOMATIC_ATHENA;
};

/**
 * Retrieves a list of test cases names contained in a result's feedback list.
 *
 * @param results list of results to extract the test case names from
 * @return list of extracted test case names
 */
export function getTestCaseNamesFromResults(results: ResultWithPointsPerGradingCriterion[]): string[] {
    const testCasesNames: Set<string> = new Set();
    results.forEach((result) => {
        if (!result.result.feedbacks) {
            return [];
        }
        result.result.feedbacks.forEach((feedback) => {
            if (Feedback.isTestCaseFeedback(feedback)) {
                testCasesNames.add(feedback.testCase?.testName ?? 'Test ' + (result.result.feedbacks!.indexOf(feedback) + 1));
            }
        });
    });
    return Array.from(testCasesNames);
}

/**
 * Extracts test case results from a given result and returns them.
 * If no feedback is found in the result an empty array is returned
 * @param result from which the test case results should be extracted
 * @param testCaseNames list containing the test names
 * @param withFeedback if true, the feedback's full text is included in case of failed test case
 */
export function getTestCaseResults(result: ResultWithPointsPerGradingCriterion, testCaseNames: string[], withFeedback?: boolean): TestCaseResult[] {
    const testCaseResults: TestCaseResult[] = [];

    testCaseNames.forEach((testName) => {
        const feedback = getFeedbackByTestCase(testName, result.result.feedbacks);

        let resultText;
        if (feedback?.positive) {
            resultText = 'Passed';
        } else {
            resultText = !!withFeedback && feedback?.detailText ? `Failed: "${feedback.detailText}"` : 'Failed';
        }
        testCaseResults.push({ testName, testResult: resultText } as TestCaseResult);
    });
    return testCaseResults;
}

/**
 * Retrieves a feedback object from a result's feedback list by a given test case name.
 *
 * If no feedback is found for the given test case name, null is returned.
 * @param feedbacks the list of result feedbacks to search in
 * @param testCase the name of the test case to search for
 */
export function getFeedbackByTestCase(testCase: string, feedbacks?: Feedback[]): Feedback | null {
    if (!feedbacks) {
        return null;
    }
    const i = feedbacks.findIndex((feedback) => feedback.testCase?.testName?.localeCompare(testCase) === 0);
    return i !== -1 ? feedbacks[i] : null;
}

/**
 * Removes references from the {@link Participation}, {@link Submission}, and {@link Feedback} back to the result itself.
 *
 * To be used before sending data to the server.
 * Otherwise, no valid JSON can be constructed.
 *
 * @param result Some result.
 */
export function breakCircularResultBackReferences(result: Result) {
    if (result.submission?.results) {
        result.submission.results = [];
    }
    if (result.feedbacks) {
        result.feedbacks.forEach((feedback) => (feedback.result = undefined));
    }
}
