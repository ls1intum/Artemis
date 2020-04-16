import { TextBlock } from 'app/entities/text-block.model';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';

export class TextBlockRef {
    constructor(public block: TextBlock, public feedback?: Feedback) {}

    public static new(): TextBlockRef {
        return new TextBlockRef(new TextBlock());
    }

    public initFeedback(): void {
        if (this.feedback) {
            return;
        }

        this.feedback = Feedback.forText(this.block);
        this.feedback.type = FeedbackType.MANUAL;
    }
}
