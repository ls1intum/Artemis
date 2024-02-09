import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { FeedbackGroup } from 'app/exercises/shared/feedback/group/feedback-group';
import { ProgrammingFeedbackItemService } from 'app/exercises/shared/feedback/item/programming-feedback-item.service';
import { Feedback, FeedbackType, STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER, SUBMISSION_POLICY_FEEDBACK_IDENTIFIER } from 'app/entities/feedback.model';
import { TranslateService } from '@ngx-translate/core';
import { FeedbackItem } from 'app/exercises/shared/feedback/item/feedback-item';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';

describe('ProgrammingFeedbackItemService', () => {
    let service: ProgrammingFeedbackItemService;
    const exercise = new ProgrammingExercise(undefined, undefined);

    beforeEach(() => {
        const fake = { instant: (key: string) => key };
        service = new ProgrammingFeedbackItemService(fake as TranslateService);
    });

    it('should create submission policy feedback item', () => {
        const feedback = {
            type: FeedbackType.AUTOMATIC,
            text: SUBMISSION_POLICY_FEEDBACK_IDENTIFIER,
            id: 1,
        };

        const expected = {
            color: 'primary',
            type: 'Submission Policy',
            name: 'artemisApp.programmingExercise.submissionPolicy.title',
            positive: false,
            title: '',
            feedbackReference: feedback,
        };

        expect(service.create([feedback], false)).toEqual([expected]);
    });

    it('should create SCA feedback item', () => {
        const feedback = createSCAFeedback();

        const expected = {
            credits: -10,
            name: 'artemisApp.result.detail.codeIssue.name',
            positive: false,
            text: 'message',
            title: 'artemisApp.result.detail.codeIssue.title',
            type: 'Static Code Analysis',
            feedbackReference: feedback,
        };

        expect(service.create([feedback], false)).toEqual([expected]);
    });

    it('should create SCA feedback item with details', () => {
        const feedback = createSCAFeedback();

        const expected = {
            credits: -10,
            name: 'artemisApp.result.detail.codeIssue.name',
            text: 'rule: message',
            positive: false,
            title: 'artemisApp.result.detail.codeIssue.title',
            type: 'Static Code Analysis',
            feedbackReference: feedback,
        };

        expect(service.create([feedback], true)).toEqual([expected]);
    });

    it('should create automatic feedback item', () => {
        const feedback = {
            id: 1,
            type: FeedbackType.AUTOMATIC,
        };

        const expected = {
            name: 'artemisApp.result.detail.test.name',
            type: 'Test',
            feedbackReference: feedback,
        };

        expect(service.create([feedback], false)).toEqual([expected]);
    });

    it('should create grading instruction feedback item', () => {
        const gradingInstruction = {
            feedback: 'gradingInstruction.feedback',
            detailText: 'gradingInstruction.detailText',
        } as Partial<GradingInstruction>;

        const feedback = {
            id: 1,
            type: FeedbackType.MANUAL,
            gradingInstruction,
        } as Feedback;

        const expected = {
            type: 'Reviewer',
            name: 'artemisApp.course.reviewer',
            text: 'gradingInstruction.feedback',
            feedbackReference: feedback,
        } as FeedbackItem;

        expect(service.create([feedback], true)).toEqual([expected]);
    });

    it('should set automatic feedback item title according to positive', () => {
        const feedback = {
            id: 1,
            type: FeedbackType.AUTOMATIC,
            positive: undefined,
            testCase: { testName: 'testCaseName' },
        } as Feedback;

        const expected = {
            title: 'artemisApp.result.detail.test.noInfo',
            name: 'artemisApp.result.detail.test.name',
            type: 'Test',
            feedbackReference: feedback,
        } as FeedbackItem;

        expect(service.create([feedback], true)).toEqual([expected]);

        {
            feedback.positive = true;

            expected.positive = true;
            expected.title = 'artemisApp.result.detail.test.passed';

            expect(service.create([feedback], true)).toEqual([expected]);
        }

        {
            feedback.positive = false;

            expected.positive = false;
            expected.title = 'artemisApp.result.detail.test.failed';
            expect(service.create([feedback], true)).toEqual([expected]);
        }
    });

    it('should show a replacement title if automatic feedback is neither positive nor negative', () => {
        const feedback = {
            id: 1,
            type: FeedbackType.AUTOMATIC,
            text: 'automaticTestCase1',
            positive: undefined,
            credits: 0.3,
            testCase: { testName: 'testCaseName' },
        };

        const expected = {
            name: 'artemisApp.result.detail.test.name',
            credits: 0.3,
            title: 'artemisApp.result.detail.test.noInfo',
            type: 'Test',
            text: undefined,
            feedbackReference: feedback,
        };

        expect(service.create([feedback], true)).toEqual([expected]);
    });

    it('should create automatic feedback item with details', () => {
        const feedback = {
            id: 1,
            type: FeedbackType.AUTOMATIC,
            testCase: { testName: 'testCaseName' },
        };

        const expected = {
            type: 'Test',
            name: 'artemisApp.result.detail.test.name',
            title: 'artemisApp.result.detail.test.noInfo',
            feedbackReference: feedback,
        };

        expect(service.create([feedback], true)).toEqual([expected]);
    });

    it('should handle not executed tests', () => {
        const feedbacks: Feedback[] = [{ testCase: { testName: 'test1' }, type: FeedbackType.AUTOMATIC, credits: 0, detailText: 'Test was not executed.' }];

        const items = service.create(feedbacks, false);
        const groups = service.group(items, exercise) as FeedbackGroup[];

        expect(groups).toBeArrayOfSize(1);

        const wrongGroup = groups.find((group) => group.name === 'wrong');
        const feedbackInGroup = wrongGroup!.members[0].feedbackReference;
        expect(feedbackInGroup).toEqual(feedbacks[0]);
    });

    it('should handle automatic feedback with missing positive attribute', () => {
        const feedbacks: Feedback[] = [{ testCase: { testName: 'test1' }, type: FeedbackType.AUTOMATIC, credits: 2 }];

        const items = service.create(feedbacks, false);
        const groups = service.group(items, exercise) as FeedbackGroup[];

        expect(groups).toBeArrayOfSize(1);

        const correctGroup = groups.find((group) => group.name === 'correct');
        const feedbackInGroup = correctGroup!.members[0].feedbackReference;
        expect(feedbackInGroup).toEqual(feedbacks[0]);
    });

    const createSCAFeedback = (): Feedback => {
        const scaIssue = {
            rule: 'rule',
            message: 'message',
            penalty: 10,
        };

        return {
            id: 1,
            type: FeedbackType.AUTOMATIC,
            text: STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER,
            detailText: JSON.stringify(scaIssue),
        };
    };
});
