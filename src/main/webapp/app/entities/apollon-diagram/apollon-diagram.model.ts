import { BaseEntity } from './../../shared';

export class ApollonDiagram implements BaseEntity {

    public id: number;
    public title: string;
    public jsonRepresentation: string;

    constructor() {
    }
}
