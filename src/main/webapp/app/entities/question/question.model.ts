import { BaseEntity } from './../../shared';

export const enum ScoringType {
    'ALL_OR_NOTHING',
    'PROPORTIONAL_CORRECT_OPTIONS',
    'TRUE_FALSE_NEUTRAL'
}

export class Question implements BaseEntity {
    constructor(
        public id?: number,
        public title?: string,
        public text?: string,
        public hint?: string,
        public explanation?: string,
        public score?: number,
        public scoringType?: ScoringType,
        public randomizeOrder?: boolean,
        public exercise?: BaseEntity,
    ) {
        this.randomizeOrder = false;
    }
}
