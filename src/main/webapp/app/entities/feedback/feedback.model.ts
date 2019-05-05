import { BaseEntity } from 'app/shared';
import { Result } from '../result';
import { ElementType } from '@ls1intum/apollon';

export const enum FeedbackType {
    AUTOMATIC = 'AUTOMATIC',
    MANUAL = 'MANUAL',
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
    public referenceType: ElementType | null;
    public referenceId: string | null;

    constructor(credits?: number, text?: string, referenceId?: string, referenceType?: ElementType) {
        this.referenceId = referenceId || null;
        this.referenceType = referenceType || null;
        this.credits = credits || 0;
        this.text = text || null;
        if (referenceType && referenceId) {
            this.reference = referenceType + ':' + referenceId;
        }
    }
}
