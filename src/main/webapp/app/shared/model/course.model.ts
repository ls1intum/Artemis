import { Moment } from 'moment';
import { IExercise } from 'app/shared/model//exercise.model';

export interface ICourse {
    id?: number;
    title?: string;
    studentGroupName?: string;
    teachingAssistantGroupName?: string;
    instructorGroupName?: string;
    startDate?: Moment;
    endDate?: Moment;
    onlineCourse?: boolean;
    exercises?: IExercise[];
}

export class Course implements ICourse {
    constructor(
        public id?: number,
        public title?: string,
        public studentGroupName?: string,
        public teachingAssistantGroupName?: string,
        public instructorGroupName?: string,
        public startDate?: Moment,
        public endDate?: Moment,
        public onlineCourse?: boolean,
        public exercises?: IExercise[]
    ) {
        this.onlineCourse = this.onlineCourse || false;
    }
}
