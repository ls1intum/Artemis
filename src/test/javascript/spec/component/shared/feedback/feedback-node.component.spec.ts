import { FeedbackNodeComponent } from 'app/exercises/shared/feedback/node/feedback-node.component';
import { FeedbackItem } from 'app/exercises/shared/feedback/item/feedback-item';
import { FeedbackGroup } from 'app/exercises/shared/feedback/group/feedback-group';

describe('FeedbackNodeComponent', () => {
    let component: FeedbackNodeComponent;

    beforeEach(() => {
        component = new FeedbackNodeComponent();
    });

    it('should set specific node type correctly for feedback item', () => {
        component.feedbackItemNode = new FeedbackItem();
        component.ngOnInit();

        expect(component.feedbackItem).toBeDefined();
    });

    it('should set specific node type correctly for feedback group', () => {
        component.feedbackItemNode = { members: [] } as unknown as FeedbackGroup;
        component.ngOnInit();

        expect(component.feedbackItemGroup).toBeDefined();
    });
});
