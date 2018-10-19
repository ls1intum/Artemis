export interface IApollonDiagram {
    id?: number;
    title?: string;
    jsonRepresentation?: string;
}

export class ApollonDiagram implements IApollonDiagram {
    constructor(public id?: number, public title?: string, public jsonRepresentation?: string) {}
}
