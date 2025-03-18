import { FeedbackItem } from 'app/exercise/feedback/item/feedback-item';
import { FeedbackColor, FeedbackNode } from 'app/exercise/feedback/node/feedback-node';

/*
 * A group of FeedbackItems as in the composite pattern.
 * NOTE: The following definition does not enforce that each group is disjunctive from each other
 */
export abstract class FeedbackGroup implements FeedbackNode {
    name: string;
    members: FeedbackItem[] = [];
    credits: number;
    maxCredits?: number;
    color?: FeedbackColor;
    /**
     * Whether the detail is open by default
     */
    open = false;

    abstract shouldContain(feedbackItem: FeedbackItem): boolean;

    /**
     * Adds all feedback items to members and recalculates the credits of this group
     * @param feedbackItems
     */
    addAllItems(feedbackItems: FeedbackItem[]): FeedbackGroup {
        feedbackItems.forEach((item) => (item.color = this.color));
        this.members = [...this.members, ...feedbackItems];
        this.calculateCredits();
        return this;
    }

    isEmpty(): boolean {
        return this.members.length === 0;
    }

    private calculateCredits(): FeedbackGroup {
        this.credits = this.members.filter((item) => item.type !== 'Subsequent').reduce((acc, item) => acc + (item.credits ?? 0), 0);
        return this;
    }
}

export const isFeedbackGroup = (node: FeedbackNode): node is FeedbackGroup => {
    return (node as FeedbackGroup).members !== undefined;
};
