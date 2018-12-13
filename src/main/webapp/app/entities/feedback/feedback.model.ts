import { BaseEntity } from 'app/shared';
import { Result } from '../result';

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

    constructor() {}
}
