import { describe, expect, it } from 'vitest';
import { getAllFeedbackGroups } from 'app/exercise/feedback/group/programming-feedback-groups';
import { FeedbackItem } from 'app/exercise/feedback/item/feedback-item';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';

describe('programming feedback groups', () => {
    const exercise = { id: 1, type: ExerciseType.PROGRAMMING, maxPoints: 10 } as Exercise;

    const reviewerItem = (credits: number, hideCredits?: boolean): FeedbackItem => ({
        name: 'Feedback',
        type: 'Reviewer',
        credits,
        hideCredits,
        feedbackReference: {} as Feedback,
    });

    const groupedBy = (items: FeedbackItem[]) =>
        getAllFeedbackGroups(exercise)
            .map((group) => group.addAllItems(items.filter((item) => group.shouldContain(item))))
            .filter((group) => !group.isEmpty());

    it('should still group feedback with hidden credits by the sign of its credits', () => {
        // AI feedback on a programming submission hides its points, but they still tell correct from wrong.
        const groups = groupedBy([reviewerItem(2.5, true), reviewerItem(-1, true), reviewerItem(0, true)]);

        expect(groups.map((group) => group.name)).toEqual(['wrong', 'info', 'correct']);
    });

    it('should leave hidden credits out of the group total', () => {
        const [correct] = groupedBy([reviewerItem(2.5, true), reviewerItem(1.5)]);

        expect(correct.name).toBe('correct');
        expect(correct.members).toHaveLength(2);
        expect(correct.credits).toBe(1.5);
    });

    it('should show no group total when all credits of the group are hidden', () => {
        const [correct] = groupedBy([reviewerItem(2.5, true), reviewerItem(1, true)]);

        expect(correct.credits).toBe(0);
    });
});
