import { TextBlock } from 'app/entities/text-block.model';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';

export class TextBlockRef {
    constructor(public block: TextBlock, public feedback?: Feedback) {}

    /**
     * creates a TextBlockRef object with a new TextBlock object
     */
    public static new(): TextBlockRef {
        return new TextBlockRef(new TextBlock());
    }

    /**
     * initiate the feedback attribute for the Text block
     */
    public initFeedback(): void {
        if (this.feedback) {
            return;
        }

        this.feedback = Feedback.forText(this.block);
        this.feedback.type = FeedbackType.MANUAL;
    }
}
