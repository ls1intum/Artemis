import { FeedbackItem, FeedbackItemType } from 'app/exercises/shared/result/detail/result-detail.component';

// NOTE: This type definition does not constrict that FeedbackItems can exactly belong to one group.
// You need to check manually if shouldContain leads to disjunctive subset.
export abstract class FeedbackItemGroup {
    name: string;
    members: FeedbackItem[];
    abstract shouldContain(feedbackItem: FeedbackItem): boolean;

    addAll(feedbackItems: FeedbackItem[]) {
        this.members = [...this.members, ...feedbackItems];
        return this;
    }

    isEmpty(): boolean {
        return this.members.length === 0;
    }
}

export const getAllFeedbackItemGroups = (): FeedbackItemGroup[] => {
    return [new FeedbackItemGroupMissing(), new FeedbackItemGroupWrong()];
};

class FeedbackItemGroupMissing extends FeedbackItemGroup {
    name = 'missing';
    members: FeedbackItem[];

    shouldContain(feedbackItem: FeedbackItem): boolean {
        return feedbackItem.type === FeedbackItemType.Test && feedbackItem.credits === 0;
    }
}

class FeedbackItemGroupWrong extends FeedbackItemGroup {
    name: 'wrong';
    members: FeedbackItem[];

    shouldContain(feedbackItem: FeedbackItem): boolean {
        return feedbackItem.type === FeedbackItemType.Test && feedbackItem.credits === 0;
    }
}
