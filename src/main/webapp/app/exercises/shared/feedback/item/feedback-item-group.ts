import { FeedbackItem } from 'app/exercises/shared/feedback/item/feedback-item';

/*
 * A group of FeedbackItems as in the composite pattern.
 * NOTE: The following definition does not enforce that each group is disjunctive from each other
 */
export abstract class FeedbackItemGroup {
    name: string;
    members: FeedbackItem[] = [];

    /**
     * bootstrap color
     */
    color: string;
    description: string;
    credits: number;

    abstract shouldContain(feedbackItem: FeedbackItem): boolean;

    addAllItems(feedbackItems: FeedbackItem[]): FeedbackItemGroup {
        this.members = [...this.members, ...feedbackItems];
        return this;
    }

    calculateCredits(): FeedbackItemGroup {
        // TODO: check what credits are
        this.credits = this.members.reduce((acc, item) => acc + (item.credits ?? 0), 0);
        return this;
    }
}
