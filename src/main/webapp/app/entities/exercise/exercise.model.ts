import { BaseEntity } from './../../shared';
import { Course } from '../course';
import { Participation } from '../participation';

export class Exercise implements BaseEntity {
    constructor(
        public id?: number,
        public title?: string,
        public type?: string,
        public releaseDate?: any,
        public dueDate?: any,
        public maxScore?: number,
        public participations?: Participation[],
        public course?: Course,
        public openForSubmission?: boolean,
        public participationStatus?: string,
        public loading?: boolean,
        public isAtLeastTutor?: boolean
    ) {
    }
}
