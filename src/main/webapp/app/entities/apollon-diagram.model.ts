import { BaseEntity } from 'app/shared/model/base-entity';
import { DiagramType } from 'app/entities/modeling-exercise.model';

export class ApollonDiagram implements BaseEntity {
    public id: number;
    public title: string;
    public jsonRepresentation: string;
    public diagramType: DiagramType;
    public courseId: number;

    constructor(diagramType: DiagramType, courseId: number) {
        this.diagramType = diagramType;
        this.courseId = courseId;
    }
}
