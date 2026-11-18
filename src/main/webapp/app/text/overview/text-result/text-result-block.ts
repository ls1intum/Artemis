import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { TextBlock } from 'app/text/shared/entities/text-block.model';
import { convertToHtmlLinebreaks, escapeString } from 'app/foundation/util/text.utils';

enum FeedbackType {
    POSITIVE = 'positive',
    NEGATIVE = 'negative',
    NEUTRAL = 'neutral',
    BLANK = 'blank',
}

export class TextResultBlock {
    readonly text: string;
    private textBlock: TextBlock;
    readonly feedback?: Feedback;

    constructor(textBlock: TextBlock, feedback?: Feedback) {
        this.textBlock = textBlock;
        this.feedback = feedback;
        this.text = convertToHtmlLinebreaks(escapeString(textBlock.text ?? ''));
    }

    get length(): number {
        return this.endIndex - this.startIndex;
    }

    get startIndex(): number {
        return this.textBlock.startIndex!;
    }

    get endIndex(): number {
        return this.textBlock.endIndex!;
    }

    get feedbackType(): FeedbackType {
        if (!this.feedback || Feedback.isEmpty(this.feedback)) {
            return FeedbackType.BLANK;
        } else if (this.feedback.credits! > 0) {
            return FeedbackType.POSITIVE;
        } else if (this.feedback.credits! < 0) {
            return FeedbackType.NEGATIVE;
        }
        return FeedbackType.NEUTRAL;
    }

    get cssClass(): string {
        return this.feedbackType && this.feedbackType !== FeedbackType.BLANK ? `text-with-feedback ${this.feedbackType}-feedback` : '';
    }
}
