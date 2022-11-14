import { ResultTemplateStatus, getResultIconClass, getTextColorClass, getUnreferencedFeedback, onlyShowSuccessfulCompileStatus } from 'app/exercises/shared/result/result.utils';
import { FeedbackType, STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER } from 'app/entities/feedback.model';
import { SubmissionExerciseType } from 'app/entities/submission.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { ParticipationType } from 'app/entities/participation/participation.model';
import { MIN_SCORE_GREEN, MIN_SCORE_ORANGE } from 'app/app.constants';
import { faCheckCircle, faQuestionCircle, faTimesCircle } from '@fortawesome/free-regular-svg-icons';
import { ExerciseType } from 'app/entities/exercise.model';
import { Result } from 'app/entities/result.model';

describe('ResultUtils', () => {
    it('should filter out all non unreferenced feedbacks', () => {
        const feedbacks = [{ reference: 'foo' }, { reference: 'foo', type: FeedbackType.MANUAL_UNREFERENCED }, { type: FeedbackType.MANUAL_UNREFERENCED }, {}];
        const unreferencedFeedbacks = getUnreferencedFeedback(feedbacks);
        expect(unreferencedFeedbacks).toEqual([{ type: FeedbackType.MANUAL_UNREFERENCED }]);
    });

    it.each([
        {
            result: { feedbacks: [{ type: FeedbackType.AUTOMATIC, text: STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER }, { type: FeedbackType.MANUAL }], testCaseCount: 0 },
            templateStatus: ResultTemplateStatus.HAS_RESULT,
            expected: true,
        },
        {
            result: { feedbacks: [{ type: FeedbackType.AUTOMATIC, text: 'This is a test case' }, { type: FeedbackType.MANUAL }], testCaseCount: 1 },
            templateStatus: ResultTemplateStatus.HAS_RESULT,
            expected: false,
        },
        {
            result: { feedbacks: [{ type: FeedbackType.AUTOMATIC, text: STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER }, { type: FeedbackType.MANUAL }], testCaseCount: 0 },
            templateStatus: ResultTemplateStatus.NO_RESULT,
            expected: false,
        },
        {
            result: { feedbacks: [{ type: FeedbackType.AUTOMATIC, text: STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER }, { type: FeedbackType.MANUAL }], testCaseCount: 0 },
            templateStatus: ResultTemplateStatus.IS_BUILDING,
            expected: false,
        },
    ])('should correctly determine if compilation is tested', ({ result, templateStatus, expected }) => {
        expect(onlyShowSuccessfulCompileStatus(result, templateStatus!)).toBe(expected);
    });

    it.each([
        { result: undefined, templateStatus: ResultTemplateStatus.HAS_RESULT, expected: 'text-secondary' },
        { result: {}, templateStatus: ResultTemplateStatus.LATE, expected: 'result-late' },
        {
            result: { submission: { submissionExerciseType: SubmissionExerciseType.PROGRAMMING, buildFailed: true }, assessmentType: AssessmentType.AUTOMATIC },
            templateStatus: ResultTemplateStatus.HAS_RESULT,
            expected: 'text-danger',
        },
        {
            result: { participation: { type: ParticipationType.PROGRAMMING, exercise: { type: ExerciseType.PROGRAMMING } } as Result, assessmentType: AssessmentType.AUTOMATIC },
            templateStatus: ResultTemplateStatus.HAS_RESULT,
            expected: 'text-secondary',
        },
        { result: { score: undefined, successful: true }, templateStatus: ResultTemplateStatus.HAS_RESULT, expected: 'text-success' },
        { result: { score: undefined, successful: false }, templateStatus: ResultTemplateStatus.HAS_RESULT, expected: 'text-danger' },
        { result: { score: MIN_SCORE_GREEN }, templateStatus: ResultTemplateStatus.HAS_RESULT, expected: 'text-success' },
        { result: { score: MIN_SCORE_ORANGE }, templateStatus: ResultTemplateStatus.HAS_RESULT, expected: 'result-orange' },
        { result: {}, templateStatus: ResultTemplateStatus.HAS_RESULT, expected: 'text-danger' },
    ])('should correctly determine text color class', ({ result, templateStatus, expected }) => {
        expect(getTextColorClass(result, templateStatus!)).toBe(expected);
    });

    it.each([
        { result: undefined, templateStatus: ResultTemplateStatus.HAS_RESULT, expected: faQuestionCircle },
        {
            result: { submission: { submissionExerciseType: SubmissionExerciseType.PROGRAMMING, buildFailed: true }, assessmentType: AssessmentType.AUTOMATIC },
            templateStatus: ResultTemplateStatus.HAS_RESULT,
            expected: faTimesCircle,
        },
        {
            result: { participation: { type: ParticipationType.PROGRAMMING, exercise: { type: ExerciseType.PROGRAMMING } } } as Result,
            templateStatus: ResultTemplateStatus.HAS_RESULT,
            expected: faQuestionCircle,
        },
        { result: {}, templateStatus: ResultTemplateStatus.HAS_RESULT, expected: faCheckCircle },
        {
            result: { score: undefined, successful: true, feedbacks: [{ type: FeedbackType.AUTOMATIC, text: 'This is a test case' }], testCaseCount: 1 },
            templateStatus: ResultTemplateStatus.HAS_RESULT,
            expected: faCheckCircle,
        },
        {
            result: { score: undefined, successful: false, feedbacks: [{ type: FeedbackType.AUTOMATIC, text: 'This is a test case' }], testCaseCount: 1 },
            templateStatus: ResultTemplateStatus.HAS_RESULT,
            expected: faTimesCircle,
        },
        {
            result: { score: MIN_SCORE_GREEN, feedbacks: [{ type: FeedbackType.AUTOMATIC, text: 'This is a test case' }], testCaseCount: 1 },
            templateStatus: ResultTemplateStatus.HAS_RESULT,
            expected: faCheckCircle,
        },
        {
            result: { score: MIN_SCORE_ORANGE, feedbacks: [{ type: FeedbackType.AUTOMATIC, text: 'This is a test case' }], testCaseCount: 1 },
            templateStatus: ResultTemplateStatus.HAS_RESULT,
            expected: faTimesCircle,
        },
        {
            result: { feedbacks: [{ type: FeedbackType.AUTOMATIC, text: 'This is a test case' }], testCaseCount: 1 },
            templateStatus: ResultTemplateStatus.HAS_RESULT,
            expected: faTimesCircle,
        },
    ])('should correctly determine result icon', ({ result, templateStatus, expected }) => {
        expect(getResultIconClass(result, templateStatus!)).toBe(expected);
    });
});
