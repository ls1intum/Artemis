import { HttpResponse } from '@angular/common/http';
import { Result } from 'app/entities/result';

export type EntityResponseType = HttpResponse<Result>;

export class TextAssessment {
    public reference: string;
    public credits: number;
    public text: string;

    constructor(reference: string, credits: number, text: string) {
        this.reference = reference;
        this.credits = credits;
        this.text = text;
    }
}
