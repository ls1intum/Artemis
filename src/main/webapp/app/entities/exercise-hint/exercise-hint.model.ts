import { Exercise } from 'app/entities/exercise';

export interface IExerciseHint {
    id?: number;
    title?: string;
    content?: string;
    exercise?: Exercise;
}

export class ExerciseHint implements IExerciseHint {
    constructor(public id?: number, public title?: string, public content?: string, public exercise?: Exercise) {}
}
