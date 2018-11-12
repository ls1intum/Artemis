export class TextAssessment {
    public text: string;
    public credits: number;
    public comment: string;

    constructor(text: string, credits: number, comment: string) {
        this.text = text;
        this.credits = credits;
        this.comment = comment;
    }
}
