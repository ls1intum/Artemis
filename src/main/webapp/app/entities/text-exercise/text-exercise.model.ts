import { Exercise, ExerciseType } from '../exercise';

export class TextExercise extends Exercise {

    public sampleSolution: string;

    constructor() {
        super(ExerciseType.TEXT);
    }
}
