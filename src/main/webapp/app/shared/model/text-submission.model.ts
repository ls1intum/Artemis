export interface ITextSubmission {
    id?: number;
    text?: string;
}

export class TextSubmission implements ITextSubmission {
    constructor(public id?: number, public text?: string) {}
}
