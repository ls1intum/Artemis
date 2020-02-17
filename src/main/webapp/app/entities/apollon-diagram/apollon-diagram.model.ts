import { BaseEntity } from 'app/shared/model/base-entity';
import { DiagramType } from 'app/entities/modeling-exercise/modeling-exercise.model';

export class ApollonDiagram implements BaseEntity {
    public id: number;
    public title: string;
    public jsonRepresentation: string;
    public diagramType: DiagramType;

    constructor(diagramType: DiagramType) {
        this.diagramType = diagramType;
    }
}
