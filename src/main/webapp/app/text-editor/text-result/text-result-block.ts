import { Feedback } from 'app/entities/feedback';

enum FeedbackType {
    POSITIVE = 'positive',
    NEGATIVE = 'negative',
    NEUTRAL = 'neutral',
}

export class TextResultBlock {
    constructor(public text: string, public startIndex: number, public feedback?: Feedback) {
        this.text = text;
        this.startIndex = startIndex;
        this.feedback = feedback;
    }

    get length(): number {
        return this.text.length;
    }

    get endIndex(): number {
        return this.startIndex + this.length;
    }

    get feedbackType(): FeedbackType {
        if (!this.feedback) {
            return null;
        } else if (this.feedback.credits > 0) {
            return FeedbackType.POSITIVE;
        } else if (this.feedback.credits < 0) {
            return FeedbackType.NEGATIVE;
        } else {
            return FeedbackType.NEUTRAL;
        }
    }

    get cssClass(): string {
        return `feedback-text-${this.feedbackType}`;
    }

    get icon(): string {
        if (this.feedbackType === FeedbackType.POSITIVE) {
            return 'faCheck';
        } else if (this.feedbackType === FeedbackType.NEGATIVE) {
            return 'faTimes';
        }
    }

    get iconCssClass(): string {
        return `feedback-icon-${this.feedbackType}`;
    }

    get feedbackCssClass(): string {
        if (this.feedbackType === FeedbackType.POSITIVE) {
            return 'alert alert-success';
        } else if (this.feedbackType === FeedbackType.NEGATIVE) {
            return 'alert alert-danger';
        } else if (this.feedbackType === FeedbackType.NEUTRAL) {
            return 'alert alert-secondary';
        }
    }
}
