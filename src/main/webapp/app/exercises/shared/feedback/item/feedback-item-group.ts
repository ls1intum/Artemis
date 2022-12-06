import { FeedbackItem } from 'app/exercises/shared/feedback/item/feedback-item';
import { FeedbackItemNode } from 'app/exercises/shared/feedback/item/feedback-item-node';

/*
 * A group of FeedbackItems as in the composite pattern.
 * NOTE: The following definition does not enforce that each group is disjunctive from each other
 */
export abstract class FeedbackItemGroup implements FeedbackItemNode {
    name: string;
    members: FeedbackItem[] = [];
    color: string;
    credits: number;
    maxCredits?: number;
    description: string;
    /**
     * Whether the detail is open by default
     */
    open = false;

    abstract shouldContain(feedbackItem: FeedbackItem): boolean;

    /**
     * Adds all feedback items to members and recalculates the credits of this group
     * @param feedbackItems
     */
    addAllItems(feedbackItems: FeedbackItem[]): FeedbackItemGroup {
        this.members = [...this.members, ...feedbackItems];
        this.calculateCredits();
        return this;
    }

    isEmpty(): boolean {
        return this.members.length === 0;
    }

    private calculateCredits(): FeedbackItemGroup {
        this.credits = this.members.reduce((acc, item) => acc + (item.credits ?? 0), 0);
        return this;
    }
}

export const isFeedbackItemGroup = (node: FeedbackItemNode): node is FeedbackItemGroup => {
    return (node as FeedbackItemGroup).members !== undefined;
};
