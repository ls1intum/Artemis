import { expect } from 'vitest';
import {
    ResultTemplateStatus,
    breakCircularResultBackReferences,
    getManualUnreferencedFeedback,
    getResultIconClass,
    getTextColorClass,
    getUnreferencedFeedback,
    isOnlyCompilationTested,
} from 'app/exercise/result/result.utils';
import { Feedback, FeedbackType, STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER } from 'app/assessment/shared/entities/feedback.model';
import { Submission, SubmissionExerciseType } from 'app/exercise/shared/entities/submission/submission.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { Participation, ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { MIN_SCORE_GREEN, MIN_SCORE_ORANGE } from 'app/app.constants';
import { faCheckCircle, faQuestionCircle, faTimesCircle } from '@fortawesome/free-regular-svg-icons';
import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import dayjs from 'dayjs/esm';

describe('ResultUtils', () => {
    it('should filter out all non unreferenced feedbacks that do not have type MANUAL_UNREFERENCED', () => {
        const feedbacks = [
            { reference: 'foo' },
            { reference: 'foo', type: FeedbackType.MANUAL_UNREFERENCED },
            { type: FeedbackType.AUTOMATIC },
            { type: FeedbackType.MANUAL_UNREFERENCED },
            {},
        ];
        const unreferencedFeedbacks = getManualUnreferencedFeedback(feedbacks);
        expect(unreferencedFeedbacks).toEqual([{ type: FeedbackType.MANUAL_UNREFERENCED }]);
    });

    it('should filter out all non unreferenced feedbacks', () => {
        const feedbacks = [
            { reference: 'foo' },
            { reference: 'foo', type: FeedbackType.AUTOMATIC },
            { type: FeedbackType.AUTOMATIC },
            { type: FeedbackType.MANUAL_UNREFERENCED },
            { reference: 'foo', type: FeedbackType.AUTOMATIC_ADAPTED },
            {},
        ];
        const unreferencedFeedbacks = getUnreferencedFeedback(feedbacks);
        expect(unreferencedFeedbacks).toEqual([{ type: FeedbackType.AUTOMATIC }, { type: FeedbackType.MANUAL_UNREFERENCED }]);
    });

    it.each([
        {
            result: {
                feedbacks: [{ type: FeedbackType.AUTOMATIC, text: STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER }, { type: FeedbackType.MANUAL }],
                testCaseCount: 0,
            } as Result,
            participation: { exercise: { type: ExerciseType.PROGRAMMING } } as Participation,
            templateStatus: ResultTemplateStatus.HAS_RESULT,
            expected: true,
        },
        {
            result: { feedbacks: [{ type: FeedbackType.AUTOMATIC, text: 'This is a test case' }, { type: FeedbackType.MANUAL }], testCaseCount: 1 },
            participation: { exercise: { type: ExerciseType.PROGRAMMING } } as Participation,
            templateStatus: ResultTemplateStatus.HAS_RESULT,
            expected: false,
        },
        {
            result: { feedbacks: [{ type: FeedbackType.AUTOMATIC, text: STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER }, { type: FeedbackType.MANUAL }], testCaseCount: 0 },
            participation: { exercise: { type: ExerciseType.PROGRAMMING } } as Participation,
            templateStatus: ResultTemplateStatus.NO_RESULT,
            expected: false,
        },
        {
            result: { feedbacks: [{ type: FeedbackType.AUTOMATIC, text: STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER }, { type: FeedbackType.MANUAL }], testCaseCount: 0 },
            participation: { exercise: { type: ExerciseType.PROGRAMMING } } as Participation,
            templateStatus: ResultTemplateStatus.IS_BUILDING,
            expected: false,
        },
    ])('should correctly determine if compilation is tested', ({ result, participation, templateStatus, expected }) => {
        expect(isOnlyCompilationTested(result, participation, templateStatus!)).toBe(expected);
    });

    it.each([
        { result: undefined, participation: {} as Participation, templateStatus: ResultTemplateStatus.HAS_RESULT, expected: 'text-secondary' },
        { result: {}, participation: {}, templateStatus: ResultTemplateStatus.LATE, expected: 'result-late' },
        {
            result: { submission: { submissionExerciseType: SubmissionExerciseType.PROGRAMMING, buildFailed: true }, assessmentType: AssessmentType.AUTOMATIC },
            participation: {} as Participation,
            templateStatus: ResultTemplateStatus.HAS_RESULT,
            expected: 'text-danger',
        },
        {
            result: {
                assessmentType: AssessmentType.AUTOMATIC,
            },
            participation: { type: ParticipationType.PROGRAMMING, exercise: { type: ExerciseType.PROGRAMMING } } as Participation,
            templateStatus: ResultTemplateStatus.HAS_RESULT,
            expected: 'text-secondary',
        },
        {
            result: { score: undefined, successful: true },
            participation: {} as Participation,
            templateStatus: ResultTemplateStatus.HAS_RESULT,
            expected: 'text-success',
        },
        {
            result: { score: 0, successful: undefined, assessmentType: AssessmentType.AUTOMATIC_ATHENA },
            participation: {} as Participation,
            templateStatus: ResultTemplateStatus.IS_GENERATING_FEEDBACK,
            expected: 'text-secondary',
        },
        {
            result: { score: 0, successful: true, assessmentType: AssessmentType.AUTOMATIC_ATHENA },
            participation: {} as Participation,
            templateStatus: ResultTemplateStatus.HAS_RESULT,
            expected: 'text-secondary',
        },
        { result: { score: undefined, successful: false }, participation: {} as Participation, templateStatus: ResultTemplateStatus.HAS_RESULT, expected: 'text-danger' },
        { result: { score: MIN_SCORE_GREEN, testCaseCount: 1 }, participation: {} as Participation, templateStatus: ResultTemplateStatus.HAS_RESULT, expected: 'text-success' },
        { result: { score: MIN_SCORE_ORANGE, testCaseCount: 1 }, participation: {} as Participation, templateStatus: ResultTemplateStatus.HAS_RESULT, expected: 'result-orange' },
        { result: {}, participation: {} as Participation, templateStatus: ResultTemplateStatus.HAS_RESULT, expected: 'text-danger' },
        {
            result: { score: 1 } as Result,
            participation: { exercise: { type: ExerciseType.PROGRAMMING } as Exercise, submissions: [{ id: 1 }] } as Participation,
            templateStatus: ResultTemplateStatus.HAS_RESULT,
            expected: 'text-success',
        },
    ])('should correctly determine text color class', ({ result, participation, templateStatus, expected }) => {
        expect(getTextColorClass(result, participation, templateStatus!)).toBe(expected);
    });

    it.each([
        {
            result: {} as Result,
            participation: { exercise: { type: ExerciseType.PROGRAMMING } as Exercise } as Participation,
            templateStatus: ResultTemplateStatus.HAS_RESULT,
            expected: faCheckCircle,
        },
        { result: undefined, participation: {} as Participation, templateStatus: ResultTemplateStatus.HAS_RESULT, expected: faQuestionCircle },
        {
            result: { submission: { submissionExerciseType: SubmissionExerciseType.PROGRAMMING, buildFailed: true }, assessmentType: AssessmentType.AUTOMATIC },
            participation: {} as Participation,
            templateStatus: ResultTemplateStatus.HAS_RESULT,
            expected: faTimesCircle,
        },
        {
            result: {} as Result,
            participation: { type: ParticipationType.PROGRAMMING, exercise: { type: ExerciseType.PROGRAMMING } as Exercise } as Participation,
            templateStatus: ResultTemplateStatus.HAS_RESULT,
            expected: faQuestionCircle,
        },
        {
            result: { score: undefined, successful: true, feedbacks: [{ type: FeedbackType.AUTOMATIC, text: 'This is a test case' }], testCaseCount: 1 },
            participation: {} as Participation,
            templateStatus: ResultTemplateStatus.HAS_RESULT,
            expected: faCheckCircle,
        },
        {
            result: { score: undefined, successful: false, feedbacks: [{ type: FeedbackType.AUTOMATIC, text: 'This is a test case' }], testCaseCount: 1 },
            participation: {} as Participation,
            templateStatus: ResultTemplateStatus.HAS_RESULT,
            expected: faTimesCircle,
        },
        {
            result: { score: MIN_SCORE_GREEN, feedbacks: [{ type: FeedbackType.AUTOMATIC, text: 'This is a test case' }], testCaseCount: 1 },
            participation: {} as Participation,
            templateStatus: ResultTemplateStatus.HAS_RESULT,
            expected: faCheckCircle,
        },
        {
            result: { score: MIN_SCORE_ORANGE, feedbacks: [{ type: FeedbackType.AUTOMATIC, text: 'This is a test case' }], testCaseCount: 1 },
            participation: {} as Participation,
            templateStatus: ResultTemplateStatus.HAS_RESULT,
            expected: faTimesCircle,
        },
        {
            result: { feedbacks: [{ type: FeedbackType.AUTOMATIC, text: 'This is a test case' }], testCaseCount: 1 },
            participation: {} as Participation,
            templateStatus: ResultTemplateStatus.HAS_RESULT,
            expected: faTimesCircle,
        },
        {
            result: {
                feedbacks: [{ type: FeedbackType.AUTOMATIC, text: 'AI result being generated test case' }],
                assessmentType: AssessmentType.AUTOMATIC_ATHENA,
                successful: undefined,
                completionDate: dayjs().add(5, 'minutes'),
            },
            participation: {} as Participation,
            templateStatus: ResultTemplateStatus.IS_GENERATING_FEEDBACK,
            expected: faCircleNotch,
        },
        {
            result: {
                feedbacks: [{ type: FeedbackType.AUTOMATIC, text: 'AI result >= 100' }],
                participation: { type: ParticipationType.STUDENT, exercise: { type: ExerciseType.TEXT } },
                successful: true,
                assessmentType: AssessmentType.AUTOMATIC_ATHENA,
                completionDate: dayjs().subtract(5, 'minutes'),
            } as Result,
            participation: {} as Participation,
            templateStatus: ResultTemplateStatus.HAS_RESULT,
            expected: faCheckCircle,
        },
        {
            result: {
                feedbacks: [{ type: FeedbackType.AUTOMATIC, text: 'AI result failed to generate' }],
            },
            participation: {
                type: ParticipationType.STUDENT,
                exercise: { type: ExerciseType.TEXT } as Exercise,
                successful: false,
                assessmentType: AssessmentType.AUTOMATIC_ATHENA,
                completionDate: dayjs().subtract(5, 'minutes'),
            } as Participation,
            templateStatus: ResultTemplateStatus.HAS_RESULT,
            expected: faTimesCircle,
        },
    ])('should correctly determine result icon', ({ result, participation, templateStatus, expected }) => {
        expect(getResultIconClass(result, participation, templateStatus!)).toBe(expected);
    });

    describe('circular reference breaker', () => {
        const baseParticipation = {} as Participation;
        const baseSubmission = {
            participation: baseParticipation,
        } as Submission;
        const baseResult = {
            submission: baseSubmission,
            participation: baseParticipation,
        } as Result;

        it('should break a reference from feedbacks back to the result', () => {
            const feedback = {
                result: baseResult,
            } as Feedback;
            baseResult.feedbacks = [feedback];

            breakCircularResultBackReferences(baseResult);

            expect(baseResult.feedbacks[0].result).toBeUndefined();
        });
    });
});
