import { Moment } from 'moment';
import { IParticipation } from 'app/shared/model/participation.model';
import { ICourse } from 'app/shared/model/course.model';

export interface IExercise {
    id?: number;
    title?: string;
    releaseDate?: Moment;
    dueDate?: Moment;
    participations?: IParticipation[];
    course?: ICourse;
}

export class Exercise implements IExercise {
    constructor(
        public id?: number,
        public title?: string,
        public releaseDate?: Moment,
        public dueDate?: Moment,
        public participations?: IParticipation[],
        public course?: ICourse
    ) {}
}
