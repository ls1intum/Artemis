import { BaseEntity } from 'app/shared';
import { Result } from '../result';
import { ElementType } from '@ls1intum/apollon';

export const enum FeedbackType {
    AUTOMATIC = 'AUTOMATIC',
    MANUAL = 'MANUAL'
}

export class Feedback implements BaseEntity {
    public id: number;
    public text: string;
    public detailText: string;
    public reference: string;
    public credits: number;
    public type: FeedbackType;
    public result: Result;
    public positive: boolean;

    // helper attributes for modeling exercise assessments stored in Feedback
    public referenceType: ElementType;
    public referenceId: string;

    constructor(referenceId?: string, referenceType?: ElementType, credits?: number, text?: string) {
        this.referenceId = referenceId;
        this.referenceType = referenceType;
        this.reference = referenceType + ':' + referenceId;
        this.credits = credits;
        this.text = text;
    }
}
