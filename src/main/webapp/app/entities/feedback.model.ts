import { BaseEntity } from 'app/shared/model/base-entity';
import { Result } from 'app/entities/result.model';

export const enum FeedbackHighlightColor {
    RED = 'rgba(219, 53, 69, 0.6)',
    CYAN = 'rgba(23, 162, 184, 0.3)',
    BLUE = 'rgba(0, 123, 255, 0.6)',
    YELLOW = 'rgba(255, 193, 7, 0.6)',
    GREEN = 'rgba(40, 167, 69, 0.6)',
}

export const enum FeedbackType {
    AUTOMATIC = 'AUTOMATIC',
    MANUAL = 'MANUAL',
    AUTOMATIC_ADAPTED = 'AUTOMATIC_ADAPTED',
}

export class Feedback implements BaseEntity {
    public id: number;
    public text: string | null;
    public detailText: string | null;
    public reference: string | null;
    public credits: number;
    public type: FeedbackType | null;
    public result: Result | null;
    public positive: boolean | null;

    // helper attributes for modeling exercise assessments stored in Feedback
    public referenceType: string | null; // this string needs to follow UMLModelElementType in Apollon in typings.d.ts
    public referenceId: string | null;

    constructor(credits?: number, text?: string, referenceId?: string, referenceType?: string) {
        this.referenceId = referenceId || null;
        this.referenceType = referenceType || null;
        this.credits = credits || 0;
        this.text = text || null;
        if (referenceType && referenceId) {
            this.reference = referenceType + ':' + referenceId;
        }
    }
}
