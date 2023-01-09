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
            text: 'feedbackText',
            detailText: 'feedbackDetailText',
            credits: 2,
        } as Feedback;

        const expected = {
            name: 'artemisApp.result.detail.feedback',
            type: 'Feedback',
            title: feedback.text,
            text: feedback.detailText,
            credits: feedback.credits,
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
