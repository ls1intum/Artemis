import { BaseEntity } from './../../shared';
import { Result } from '../result';

export const enum FeedbackType {
    'AUTOMATIC',
    'MANUAL'
}

export class Feedback implements BaseEntity {
    constructor(
        public id?: number,
        public text?: string,
        public detailText?: string,
        public type?: FeedbackType,
        public result?: Result,
        public positive?: boolean,
    ) {
    }
}
