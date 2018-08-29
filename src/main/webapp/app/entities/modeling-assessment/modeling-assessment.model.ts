export const enum ModelElementType {
    CLASS = 'class',
    ATTRIBUTE = 'attribute',
    METHOD = 'method',
    RELATIONSHIP = 'relationship'
}

export class ModelingAssessment {

    public id: string;
    public type: ModelElementType;
    public credits: number;
    public comment: string;

    constructor(id: string, type: ModelElementType, credits: number, comment: string) {
        this.id = id;
        this.type = type;
        this.credits = credits;
        this.comment = comment;
    }
}
