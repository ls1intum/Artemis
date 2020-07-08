import { BaseEntity } from 'app/shared/model/base-entity';
import { Result } from 'app/entities/result.model';
import { TextBlock } from 'app/entities/text-block.model';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';

export enum FeedbackHighlightColor {
    RED = 'rgba(219, 53, 69, 0.6)',
    CYAN = 'rgba(23, 162, 184, 0.3)',
    BLUE = 'rgba(0, 123, 255, 0.6)',
    YELLOW = 'rgba(255, 193, 7, 0.6)',
    GREEN = 'rgba(40, 167, 69, 0.6)',
}

export enum FeedbackType {
    AUTOMATIC = 'AUTOMATIC',
    MANUAL = 'MANUAL',
    MANUAL_UNREFERENCED = 'MANUAL_UNREFERENCED',
    AUTOMATIC_ADAPTED = 'AUTOMATIC_ADAPTED',
}

export class Feedback implements BaseEntity {
    public id: number;
    public gradingInstruction: GradingInstruction | null;
    public text: string | null;
    public detailText: string | null;
    public reference: string | null;
    public credits = 0;
    public type: FeedbackType | null;
    public result: Result | null;
    public positive: boolean | null;

    // helper attributes for modeling exercise assessments stored in Feedback
    public referenceType: string | null; // this string needs to follow UMLModelElementType in Apollon in typings.d.ts
    public referenceId: string | null;

    public static hasDetailText(that: Feedback): boolean {
        return that.detailText != null && that.detailText.length > 0;
    }

    public static isEmpty(that: Feedback): boolean {
        return that.credits === 0 && !Feedback.hasDetailText(that);
    }

    public static isPresent(that: Feedback): boolean {
        return !Feedback.isEmpty(that);
    }

    public static forModeling(credits: number, text?: string, referenceId?: string, referenceType?: string): Feedback {
        const that = new Feedback();
        that.referenceId = referenceId || null;
        that.referenceType = referenceType || null;
        that.credits = credits;
        that.text = text || null;
        if (referenceType && referenceId) {
            that.reference = referenceType + ':' + referenceId;
        }
        return that;
    }

    public static forText(textBlock: TextBlock, credits = 0, detailText?: string): Feedback {
        const that = new Feedback();
        that.reference = textBlock.id;
        that.credits = credits;
        that.detailText = detailText || null;

        // Delete unused properties
        delete that.referenceId;
        delete that.referenceType;
        delete that.text;
        delete that.positive;

        return that;
    }

    public static fromServerResponse(response: Feedback): Feedback {
        return Object.assign(new Feedback(), response);
    }

    public static updateFeedbackTypeOnChange(feedback: Feedback) {
        if (feedback.type === FeedbackType.AUTOMATIC) {
            feedback.type = FeedbackType.AUTOMATIC_ADAPTED;
        }
    }
}
