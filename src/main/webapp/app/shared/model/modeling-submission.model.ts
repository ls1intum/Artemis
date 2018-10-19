export interface IModelingSubmission {
    id?: number;
    model?: string;
    explanationText?: string;
}

export class ModelingSubmission implements IModelingSubmission {
    constructor(public id?: number, public model?: string, public explanationText?: string) {}
}
