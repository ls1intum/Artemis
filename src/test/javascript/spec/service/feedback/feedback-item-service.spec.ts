import { FeedbackItemServiceImpl } from 'app/exercises/shared/feedback/item/feedback-item-service';
import { TranslateService } from '@ngx-translate/core';
import { Feedback } from 'app/entities/feedback.model';
import { FeedbackItem } from 'app/exercises/shared/feedback/item/feedback-item';
import { FeedbackGroup, isFeedbackGroup } from 'app/exercises/shared/feedback/group/feedback-group';
import { FeedbackNode } from 'app/exercises/shared/feedback/node/feedback-node';

describe('FeedbackItemService', () => {
    let service: FeedbackItemServiceImpl;

    beforeEach(() => {
        const fake = { instant: (key: string) => key };
        service = new FeedbackItemServiceImpl(fake as TranslateService);
    });

    it('should create generic feedback item', () => {
        const feedback = {
            id: 1,
            text: 'feedbackText',
            detailText: 'feedbackDetailText',
            credits: 2,
        } as Feedback;

        const expected = {
            name: 'artemisApp.result.detail.feedback',
            type: 'Reviewer',
            title: feedback.text,
            text: feedback.detailText,
            credits: feedback.credits,
            feedbackReference: feedback,
        } as FeedbackItem;

        expect(service.create([feedback], false)).toEqual([expected]);
    });

    it('should create grading instruction feedback', () => {
        const feedback = { id: 1, text: 'text', detailText: 'detailText', gradingInstruction: { feedback: 'GI feedback' } } as Feedback;

        const expected = {
            name: 'artemisApp.result.detail.feedback',
            text: 'GI feedback\ndetailText',
            title: 'text',
            type: 'Reviewer',
            feedbackReference: feedback,
        } as FeedbackItem;

        expect(service.create([feedback], false)).toEqual([expected]);
    });

    it('should propagate subsequent to feedback item type', () => {
        const feedback = { id: 1, isSubsequent: true, text: 'text', detailText: 'detailText', gradingInstruction: { feedback: 'GI feedback' } } as Feedback;

        const expected = {
            name: 'artemisApp.result.detail.feedback',
            text: 'GI feedback\ndetailText',
            title: 'text',
            type: 'Subsequent',
            feedbackReference: feedback,
        } as FeedbackItem;

        expect(service.create([feedback], false)).toEqual([expected]);
    });

    it('should group feedback items correctly', () => {
        const feedbacks = [
            { text: 'pos1', credits: 10 },
            { text: 'pos2', credits: 1 },
            { text: 'neutral1', credits: 0 },
            { text: 'neutral2', credits: 0 },
            { text: 'neutral3', credits: 0 },
            { text: 'positive1', credits: -10 },
            { text: 'positive2', credits: -8 },
            { text: 'positive3', credits: -5 },
            { text: 'positive4', credits: -3 },
        ];

        const items = service.create(feedbacks, false);
        const groups = service.group(items) as FeedbackGroup[];

        expect(groups.find((group) => group.name === 'wrong')?.members).toBeArrayOfSize(4);
        expect(groups.find((group) => group.name === 'info')?.members).toBeArrayOfSize(3);
        expect(groups.find((group) => group.name === 'correct')?.members).toBeArrayOfSize(2);
    });

    it('should filter out subsequent SGI feedback for group credit calculation', () => {
        const gradingInstruction = {
            feedback: 'grading instruction feedback',
        };

        const feedbacks = [
            { gradingInstruction, isSubsequent: true, text: 'pos1', credits: 1 },
            { gradingInstruction, text: 'positive', credits: 2 },
        ] as Feedback[];

        const items = service.create(feedbacks, false);
        const groups = service.group(items) as FeedbackGroup[];

        expect(groups).toBeArrayOfSize(2);

        const infoGroup = groups.find((group) => group.name === 'info');
        expect(infoGroup?.credits).toBe(0);
    });

    it('should have a custom type guard function that works', () => {
        const feedbackGroup = {
            name: 'feedbackGroup',
            members: [],
        } as Partial<FeedbackGroup>;

        expect(isFeedbackGroup(<FeedbackNode>feedbackGroup)).toBeTrue();

        const feedbackItem = {
            name: 'feedbackItem',
        } as Partial<FeedbackGroup>;

        expect(isFeedbackGroup(<FeedbackNode>feedbackItem)).toBeFalse();
    });
});
