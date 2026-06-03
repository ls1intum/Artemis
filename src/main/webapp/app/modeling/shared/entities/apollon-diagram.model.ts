import { UMLDiagramType } from '@tumaet/apollon';
import { BaseEntity } from 'app/foundation/model/base-entity';

export class ApollonDiagram implements BaseEntity {
    public id?: number;
    public title?: string;
    public jsonRepresentation?: string;
    public diagramType?: UMLDiagramType;
    public courseId?: number;

    constructor(diagramType: UMLDiagramType, courseId: number) {
        this.diagramType = diagramType;
        this.courseId = courseId;
    }
}
