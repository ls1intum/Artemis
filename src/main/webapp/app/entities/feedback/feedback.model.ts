import { BaseEntity } from 'app/shared';
import { Result } from '../result';
import { ModelElementType } from 'app/entities/modeling-assessment/uml-element.model';

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
    public referenceType: ModelElementType;
    public referenceId: string;

    constructor(referenceId?: string, referenceType?: ModelElementType, credits?: number, text?: string) {
        this.referenceId = referenceId;
        this.referenceType = referenceType;
        this.reference = referenceType + ':' + referenceId;
        this.credits = credits;
        this.text = text;
    }
}
