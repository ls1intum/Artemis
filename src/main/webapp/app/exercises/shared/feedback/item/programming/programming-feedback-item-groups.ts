import { FeedbackItem, FeedbackItemType } from 'app/exercises/shared/result/detail/result-detail.component';

// NOTE: This type definition does not constrict that FeedbackItems can exactly belong to one group.
// You need to check manually if shouldContain leads to disjunctive subset.
export abstract class FeedbackItemGroup {
    name: string;
    members: FeedbackItem[] = [];

    abstract shouldContain(feedbackItem: FeedbackItem): boolean;

    addAllItems(feedbackItems: FeedbackItem[]) {
        this.members = [...this.members, ...feedbackItems];
        return this;
    }
}

export const getAllFeedbackItemGroups = (): FeedbackItemGroup[] => {
    return [new FeedbackItemGroupAll(), new FeedbackItemGroupMissing(), new FeedbackItemGroupWrong()];
};

class FeedbackItemGroupAll extends FeedbackItemGroup {
    name = 'all';

    shouldContain(feedbackItem: FeedbackItem): boolean {
        return true;
    }
}

class FeedbackItemGroupMissing extends FeedbackItemGroup {
    name = 'missing';

    shouldContain(feedbackItem: FeedbackItem): boolean {
        return feedbackItem.type === FeedbackItemType.Test && feedbackItem.credits === 0;
    }
}

class FeedbackItemGroupWrong extends FeedbackItemGroup {
    name = 'wrong';

    shouldContain(feedbackItem: FeedbackItem): boolean {
        return feedbackItem.type === FeedbackItemType.Test && feedbackItem.credits === 0;
    }
}
