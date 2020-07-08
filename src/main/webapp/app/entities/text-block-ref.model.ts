import { TextBlock, TextBlockType } from 'app/entities/text-block.model';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';

export class TextBlockRef {
    constructor(public block: TextBlock, public feedback?: Feedback) {}

    public static new(): TextBlockRef {
        const textBlock = new TextBlock();
        textBlock.type = TextBlockType.MANUAL;
        return new TextBlockRef(textBlock);
    }

    public initFeedback(): void {
        if (this.feedback) {
            return;
        }

        this.feedback = Feedback.forText(this.block);
        this.feedback.type = FeedbackType.MANUAL;
    }
}
