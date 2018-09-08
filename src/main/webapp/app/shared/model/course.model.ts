import { IExercise } from 'app/shared/model/exercise.model';

export interface ICourse {
    id?: number;
    title?: string;
    studentGroupName?: string;
    teachingAssistantGroupName?: string;
    exercises?: IExercise[];
}

export class Course implements ICourse {
    constructor(
        public id?: number,
        public title?: string,
        public studentGroupName?: string,
        public teachingAssistantGroupName?: string,
        public exercises?: IExercise[]
    ) {}
}
