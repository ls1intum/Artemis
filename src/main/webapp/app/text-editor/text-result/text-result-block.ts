import { Feedback } from 'app/entities/feedback';

export class TextResultBlock {
    constructor(text: string, startIndex: number, feedback?: Feedback) {
        this.text = text;
        this.position = [startIndex, text.length];
        this.feedback = feedback;
    }

    text: string;
    position: [number, number]; // start index, length
    feedback?: Feedback;

    get startIndex(): number {
        return this.position[0];
    }

    get length(): number {
        return this.position[1];
    }

    get endIndex(): number {
        return this.startIndex + this.length;
    }

    get cssClass(): string {
        if (!this.feedback) {
            return '';
        } else if (this.feedback.credits > 0) {
            return 'feedback-text-positive';
        } else if (this.feedback.credits < 0) {
            return 'feedback-text-negative';
        } else {
            return 'feedback-text-neutral';
        }
    }
}
