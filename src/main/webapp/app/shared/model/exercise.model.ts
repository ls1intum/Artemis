import { Moment } from 'moment';
import { IParticipation } from 'app/shared/model//participation.model';
import { ICourse } from 'app/shared/model//course.model';

export const enum DifficultyLevel {
    EASY = 'EASY',
    MEDIUM = 'MEDIUM',
    HARD = 'HARD'
}

export interface IExercise {
    id?: number;
    problemStatement?: string;
    gradingInstructions?: string;
    title?: string;
    releaseDate?: Moment;
    dueDate?: Moment;
    maxScore?: number;
    difficulty?: DifficultyLevel;
    categories?: string;
    participations?: IParticipation[];
    course?: ICourse;
}

export class Exercise implements IExercise {
    constructor(
        public id?: number,
        public problemStatement?: string,
        public gradingInstructions?: string,
        public title?: string,
        public releaseDate?: Moment,
        public dueDate?: Moment,
        public maxScore?: number,
        public difficulty?: DifficultyLevel,
        public categories?: string,
        public participations?: IParticipation[],
        public course?: ICourse
    ) {}
}
