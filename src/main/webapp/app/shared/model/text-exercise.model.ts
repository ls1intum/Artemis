export interface ITextExercise {
    id?: number;
    sampleSolution?: string;
}

export class TextExercise implements ITextExercise {
    constructor(public id?: number, public sampleSolution?: string) {}
}
