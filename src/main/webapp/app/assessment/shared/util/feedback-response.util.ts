import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { ProgrammingExerciseTestCase, ProgrammingExerciseTestCaseType, Visibility } from 'app/programming/shared/entities/programming-exercise-test-case.model';
import type { Feedback as FeedbackResponse } from 'app/openapi/model/feedback';
import type { TestCase } from 'app/openapi/model/test-case';

const feedbackTypeByResponse: Record<NonNullable<FeedbackResponse['type']>, FeedbackType> = {
    AUTOMATIC: FeedbackType.AUTOMATIC,
    AUTOMATIC_ADAPTED: FeedbackType.AUTOMATIC_ADAPTED,
    MANUAL: FeedbackType.MANUAL,
    MANUAL_UNREFERENCED: FeedbackType.MANUAL_UNREFERENCED,
};

const visibilityByResponse: Record<NonNullable<TestCase['visibility']>, Visibility> = {
    ALWAYS: Visibility.Always,
    AFTER_DUE_DATE: Visibility.AfterDueDate,
    NEVER: Visibility.Never,
};

const testCaseTypeByResponse: Record<NonNullable<TestCase['type']>, ProgrammingExerciseTestCaseType> = {
    STRUCTURAL: ProgrammingExerciseTestCaseType.STRUCTURAL,
    BEHAVIORAL: ProgrammingExerciseTestCaseType.BEHAVIORAL,
    DEFAULT: ProgrammingExerciseTestCaseType.DEFAULT,
};

function toTestCase(testCase: TestCase): ProgrammingExerciseTestCase {
    return {
        id: testCase.id,
        testName: testCase.testName,
        weight: testCase.weight,
        active: testCase.active,
        bonusMultiplier: testCase.bonusMultiplier,
        bonusPoints: testCase.bonusPoints,
        visibility: testCase.visibility ? visibilityByResponse[testCase.visibility] : undefined,
        type: testCase.type ? testCaseTypeByResponse[testCase.type] : undefined,
    };
}

/**
 * Builds the feedback list of a result from the result details response and links each feedback back to its result.
 */
export function toFeedbacksOfResult(feedbacks: FeedbackResponse[], result: Result): Feedback[] {
    return feedbacks.map((response) => {
        const feedback = toFeedback(response);
        feedback.result = result;
        return feedback;
    });
}

/**
 * Builds the client feedback from a feedback of the generated client. Absent fields stay absent, so a feedback without credits is not turned into one with 0 credits.
 */
export function toFeedback(feedback: FeedbackResponse): Feedback {
    const instruction = feedback.gradingInstruction;
    return {
        id: feedback.id,
        text: feedback.text,
        detailText: feedback.detailText,
        hasLongFeedbackText: feedback.hasLongFeedbackText,
        reference: feedback.reference,
        credits: feedback.credits,
        positive: feedback.positive,
        type: feedback.type ? feedbackTypeByResponse[feedback.type] : undefined,
        gradingInstruction: instruction && {
            id: instruction.id,
            credits: instruction.credits ?? 0,
            gradingScale: instruction.gradingScale ?? '',
            instructionDescription: instruction.instructionDescription ?? '',
            feedback: instruction.feedback ?? '',
            usageCount: instruction.usageCount,
        },
        testCase: feedback.testCase && toTestCase(feedback.testCase),
    };
}
