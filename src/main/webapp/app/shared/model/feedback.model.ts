import { IResult } from 'app/shared/model/result.model';

export interface IFeedback {
    id?: number;
    text?: string;
    detailText?: string;
    result?: IResult;
}

export class Feedback implements IFeedback {
    constructor(public id?: number, public text?: string, public detailText?: string, public result?: IResult) {}
}
