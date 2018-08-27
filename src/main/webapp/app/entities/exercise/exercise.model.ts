import { BaseEntity } from './../../shared';
import { Course } from '../course';
import { Participation } from '../participation';

export const enum DifficultyLevel {
    'EASY',
    'MEDIUM',
    'HARD'
}

export class Exercise implements BaseEntity {
    constructor(
        public id?: number,
        public problemStatement?: string,
        public gradingInstructions?: string,
        public title?: string,
        public type?: string,
        public releaseDate?: any,
        public dueDate?: any,
        public maxScore?: number,
        public difficulty?: DifficultyLevel,
        public categories?: string[],
        public participations?: Participation[],
        public course?: Course,
        public openForSubmission?: boolean,
        public participationStatus?: string,
        public loading?: boolean,
        public isAtLeastTutor?: boolean
    ) {
    }
}
