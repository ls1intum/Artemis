export interface IModelingSubmission {
    id?: number;
    submissionPath?: string;
}

export class ModelingSubmission implements IModelingSubmission {
    constructor(public id?: number, public submissionPath?: string) {}
}
