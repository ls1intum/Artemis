import { BaseEntity } from 'app/shared';
import { DiagramType } from 'app/entities/modeling-exercise';

export class ApollonDiagram implements BaseEntity {
    public id: number;
    public title: string;
    public jsonRepresentation: string;
    public diagramType: DiagramType;

    constructor(diagramType: DiagramType) {
        this.diagramType = diagramType;
    }
}
