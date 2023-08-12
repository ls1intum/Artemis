import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faCheck, faCheckCircle, faCircle, faDotCircle, faTimes, faTimesCircle } from '@fortawesome/free-solid-svg-icons';
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

    constructor(public textBlock: TextBlock, public feedback?: Feedback) {
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

    get icon(): IconProp | undefined {
        switch (this.feedbackType) {
            case FeedbackType.POSITIVE:
                return faCheck;
            case FeedbackType.NEGATIVE:
                return faTimes;
            case FeedbackType.NEUTRAL:
                return faCircle;
        }
    }

    get circleIcon(): IconProp | undefined {
        switch (this.feedbackType) {
            case FeedbackType.POSITIVE:
                return faCheckCircle;
            case FeedbackType.NEGATIVE:
                return faTimesCircle;
            case FeedbackType.NEUTRAL:
                return faDotCircle;
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
}
