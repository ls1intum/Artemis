export interface IProgrammingSubmission {
    id?: number;
    commitHash?: string;
}

export class ProgrammingSubmission implements IProgrammingSubmission {
    constructor(public id?: number, public commitHash?: string) {}
}
