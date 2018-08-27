import { Exercise } from '../exercise';

export class TextExercise extends Exercise {
    constructor(
        public id?: number,
        public sampleSolution?: string,
    ) {
        super();
    }
}
