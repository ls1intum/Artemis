import { expect } from 'vitest';
import {
    MissingResultInformation,
    ResultTemplateStatus,
    breakCircularResultBackReferences,
    evaluateTemplateStatus,
    getManualUnreferencedFeedback,
    getResultIconClass,
    getTextColorClass,
    getUnreferencedFeedback,
    isOnlyCompilationTested,
} from 'app/exercise/result/result.utils';
import { Feedback, FeedbackType, STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER } from 'app/assessment/shared/entities/feedback.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { Participation, ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { MIN_SCORE_GREEN, MIN_SCORE_ORANGE } from 'app/app.constants';
import { faCheckCircle, faQuestionCircle, faTimesCircle } from '@fortawesome/free-regular-svg-icons';
import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import dayjs from 'dayjs/esm';
import { SubmissionExerciseType } from 'app/exercise/shared/entities/submission/submission-exercise-type.model';

describe('ResultUtils', () => {
    describe('evaluateTemplateStatus exercise-type discrimination', () => {
        const ratedProgrammingResult: Result = { id: 1, score: 100, rated: true, completionDate: dayjs().subtract(1, 'minute') };
        const programmingExercise = { id: 6, type: ExerciseType.PROGRAMMING } as Exercise;

        it('returns HAS_RESULT for a programming participation whose embedded exercise was stripped server-side', () => {
            // The course-overview sidebar receives participations from the for-dashboard endpoint, which nulls
            // participation.exercise. The status must be derived from the explicit `exercise` argument; otherwise it
            // wrongly falls through to NO_RESULT even though a valid rated result is present (regression guard).
            const participationWithoutExercise = { id: 18, type: ParticipationType.PROGRAMMING } as Participation;
            const status = evaluateTemplateStatus(programmingExercise, participationWithoutExercise, ratedProgrammingResult, false);
            expect(status).toBe(ResultTemplateStatus.HAS_RESULT);
        });

        it('returns HAS_RESULT for a programming participation that also carries its exercise', () => {
            const participationWithExercise = { id: 18, type: ParticipationType.PROGRAMMING, exercise: programmingExercise } as Participation;
            const status = evaluateTemplateStatus(programmingExercise, participationWithExercise, ratedProgrammingResult, false);
            expect(status).toBe(ResultTemplateStatus.HAS_RESULT);
        });
    });

    describe('evaluateTemplateStatus computes each status', () => {
        const programmingExercise = { id: 6, type: ExerciseType.PROGRAMMING } as Exercise;
        const programmingParticipation = { type: ParticipationType.PROGRAMMING } as Participation;
        const textExerciseWith = (dueDate: dayjs.Dayjs, assessmentDueDate?: dayjs.Dayjs) => ({ id: 5, type: ExerciseType.TEXT, dueDate, assessmentDueDate }) as Exercise;
        const textParticipationWith = (submissionDate: dayjs.Dayjs, results?: Result[]) =>
            ({ type: ParticipationType.STUDENT, submissions: [{ id: 1, submissionDate, results }] }) as Participation;

        it('IS_BUILDING when a build is running', () => {
            expect(evaluateTemplateStatus(programmingExercise, programmingParticipation, undefined, true)).toBe(ResultTemplateStatus.IS_BUILDING);
        });

        it('IS_QUEUED when a build is queued', () => {
            expect(evaluateTemplateStatus(programmingExercise, programmingParticipation, undefined, false, MissingResultInformation.NONE, true)).toBe(
                ResultTemplateStatus.IS_QUEUED,
            );
        });

        it('MISSING when missing-result information is present', () => {
            expect(evaluateTemplateStatus(programmingExercise, programmingParticipation, undefined, false, MissingResultInformation.FAILED_PROGRAMMING_SUBMISSION_ONLINE_IDE)).toBe(
                ResultTemplateStatus.MISSING,
            );
        });

        it('IS_GENERATING_FEEDBACK for an Athena result still being processed', () => {
            const result = { id: 1, assessmentType: AssessmentType.AUTOMATIC_ATHENA, successful: undefined, completionDate: dayjs().add(1, 'hour') } as Result;
            expect(evaluateTemplateStatus(programmingExercise, programmingParticipation, result, false)).toBe(ResultTemplateStatus.IS_GENERATING_FEEDBACK);
        });

        it('FEEDBACK_GENERATION_FAILED for a failed Athena result', () => {
            const result = { id: 1, score: 50, assessmentType: AssessmentType.AUTOMATIC_ATHENA, successful: false } as Result;
            expect(evaluateTemplateStatus(programmingExercise, programmingParticipation, result, false)).toBe(ResultTemplateStatus.FEEDBACK_GENERATION_FAILED);
        });

        it('FEEDBACK_GENERATION_TIMED_OUT for a timed-out Athena result', () => {
            const result = { id: 1, score: 50, assessmentType: AssessmentType.AUTOMATIC_ATHENA, successful: undefined, completionDate: dayjs().subtract(1, 'hour') } as Result;
            expect(evaluateTemplateStatus(programmingExercise, programmingParticipation, result, false)).toBe(ResultTemplateStatus.FEEDBACK_GENERATION_TIMED_OUT);
        });

        it('NO_RESULT for a programming exercise without a result', () => {
            expect(evaluateTemplateStatus(programmingExercise, programmingParticipation, undefined, false)).toBe(ResultTemplateStatus.NO_RESULT);
        });

        it('SUBMITTED for a text exercise submitted in due time without a result', () => {
            const exercise = textExerciseWith(dayjs().add(1, 'day'));
            const participation = textParticipationWith(dayjs().subtract(1, 'hour'));
            expect(evaluateTemplateStatus(exercise, participation, undefined, false)).toBe(ResultTemplateStatus.SUBMITTED);
        });

        it('SUBMITTED_WAITING_FOR_GRADING for a manual result while the assessment period is still active', () => {
            const exercise = textExerciseWith(dayjs().add(1, 'day'), dayjs().add(2, 'day'));
            const result = { id: 1, score: 80, assessmentType: AssessmentType.MANUAL } as Result;
            const participation = textParticipationWith(dayjs().subtract(1, 'hour'), [result]);
            expect(evaluateTemplateStatus(exercise, participation, result, false)).toBe(ResultTemplateStatus.SUBMITTED_WAITING_FOR_GRADING);
        });

        it('LATE for a result submitted after the due date with no assessment due date', () => {
            const exercise = textExerciseWith(dayjs().subtract(1, 'day'));
            const result = { id: 1, score: 80, assessmentType: AssessmentType.MANUAL } as Result;
            const participation = textParticipationWith(dayjs().subtract(2, 'hour'), [result]);
            expect(evaluateTemplateStatus(exercise, participation, result, false)).toBe(ResultTemplateStatus.LATE);
        });

        it('LATE_NO_FEEDBACK for a late submission with no result', () => {
            const exercise = textExerciseWith(dayjs().subtract(1, 'day'));
            const participation = textParticipationWith(dayjs().subtract(2, 'hour'));
            expect(evaluateTemplateStatus(exercise, participation, undefined, false)).toBe(ResultTemplateStatus.LATE_NO_FEEDBACK);
        });
    });

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
