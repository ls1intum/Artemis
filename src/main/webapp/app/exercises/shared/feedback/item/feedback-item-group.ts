import { FeedbackItem } from 'app/exercises/shared/result/detail/result-detail.component';

/*
 * A group of FeedbackItems as in the composite pattern.
 * NOTE: The following definition does not enforce that each group is disjunctive from each other
 */
export abstract class FeedbackItemGroup {
    name: string;
    members: FeedbackItem[] = [];

    abstract shouldContain(feedbackItem: FeedbackItem): boolean;

    addAllItems(feedbackItems: FeedbackItem[]) {
        this.members = [...this.members, ...feedbackItems];
        return this;
    }
}
