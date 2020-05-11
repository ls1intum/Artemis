import { Feedback } from 'app/entities/feedback.model';
import { TextBlock } from 'app/entities/text-block.model';
import { convertToHtmlLinebreaks, escapeString } from 'app/utils/text.utils';

enum FeedbackType {
    POSITIVE = 'positive',
    NEGATIVE = 'negative',
    NEUTRAL = 'neutral',
    BLANK = 'blank',
}

export class TextResultBlock {
    public readonly text: string;
    private rating: number;

    constructor(public textBlock: TextBlock, public feedback?: Feedback, rating = 0) {
        this.text = convertToHtmlLinebreaks(escapeString(textBlock.text));
        this.rating = rating;
    }

    get length(): number {
        return this.endIndex - this.startIndex;
    }

    get startIndex(): number {
        return this.textBlock.startIndex;
    }

    get endIndex(): number {
        return this.textBlock.endIndex;
    }

    get feedbackType(): FeedbackType {
        if (!this.feedback || this.feedback.credits == null) {
            return FeedbackType.BLANK;
        } else if (this.feedback.credits > 0) {
            return FeedbackType.POSITIVE;
        } else if (this.feedback.credits < 0) {
            return FeedbackType.NEGATIVE;
        }
        return FeedbackType.NEUTRAL;
    }

    get cssClass(): string {
        return this.feedbackType && this.feedbackType !== FeedbackType.BLANK ? `text-with-feedback ${this.feedbackType}-feedback` : '';
    }

    get icon(): string | null {
        switch (this.feedbackType) {
            case FeedbackType.POSITIVE:
                return 'check';
            case FeedbackType.NEGATIVE:
                return 'times';
            case FeedbackType.NEUTRAL:
                return 'dot';
            default:
                return null;
        }
    }

    get iconCssClass(): string {
        return this.feedbackType ? `feedback-icon ${this.feedbackType}-feedback` : '';
    }

    get feedbackCssClass(): string {
        switch (this.feedbackType) {
            case FeedbackType.POSITIVE:
                return 'alert alert-success';
            case FeedbackType.NEGATIVE:
                return 'alert alert-danger';
            case FeedbackType.NEUTRAL:
                return 'alert alert-secondary';
            default:
                return '';
        }
    }

    getRating(): number {
        return this.rating;
    }

    setRating(rating: number): void {
        this.rating = rating;
    }
}
