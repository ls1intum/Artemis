export interface IModelingExercise {
    id?: number;
    baseFilePath?: string;
}

export class ModelingExercise implements IModelingExercise {
    constructor(public id?: number, public baseFilePath?: string) {}
}
