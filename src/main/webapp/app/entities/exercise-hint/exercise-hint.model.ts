import { Exercise } from 'app/entities/exercise';

export class ExerciseHint {
    constructor(public id?: number, public title?: string, public content?: string, public exercise?: Exercise) {}
}
