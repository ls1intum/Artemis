import { TextBlock, TextBlockType } from 'app/entities/text-block.model';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';

export class TextBlockRef {
    public block?: TextBlock;
    public feedback?: Feedback;
    // properties that just exist on the client side
    public selectable = true;
    public highlighted = false;
    public deletable = true;

    constructor(block: TextBlock, feedback?: Feedback) {
        this.block = block;
        this.feedback = feedback;
    }

    public static new(): TextBlockRef {
        const textBlock = new TextBlock();
        textBlock.type = TextBlockType.MANUAL;
        return new TextBlockRef(textBlock);
    }

    public initFeedback(): void {
        if (this.feedback) {
            return;
        }

        if (this.block) {
            this.feedback = Feedback.forText(this.block);
            this.feedback.type = FeedbackType.MANUAL;
        }
    }
}
