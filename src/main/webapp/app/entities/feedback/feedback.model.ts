import { BaseEntity } from 'app/shared';
import { Result } from '../result';
import { ElementType } from '@ls1intum/apollon';

export const enum FeedbackType {
    AUTOMATIC = 'AUTOMATIC',
    MANUAL = 'MANUAL',
}

export class Feedback implements BaseEntity {
    public id: number;
    public detailText: string;
    public reference: string;
    public type: FeedbackType;
    public result: Result;
    public positive: boolean;

    constructor(
        // helper attributes for modeling exercise assessments stored in Feedback
        public referenceId: string | null,
        public referenceType: ElementType | null,

        public credits: number | null,
        public text: string | null,
    ) {
        this.reference = referenceType + ':' + referenceId;
    }
}
