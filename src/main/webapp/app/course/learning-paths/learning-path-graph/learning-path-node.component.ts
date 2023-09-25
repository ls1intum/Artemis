import { Component, Input } from '@angular/core';
import { NgxLearningPathNode, NodeType, getIcon } from 'app/entities/competency/learning-path.model';
import { Competency, CompetencyProgress } from 'app/entities/competency.model';
import { Exercise } from 'app/entities/exercise.model';
import { Lecture } from 'app/entities/lecture.model';
import { LectureUnitForLearningPathNodeDetailsDTO } from 'app/entities/lecture-unit/lectureUnit.model';

class NodeDetailsData {
    competency?: Competency;
    competencyProgress?: CompetencyProgress;
    exercise?: Exercise;
    lecture?: Lecture;
    lectureUnit?: LectureUnitForLearningPathNodeDetailsDTO;
}

@Component({
    selector: 'jhi-learning-path-graph-node',
    templateUrl: './learning-path-node.component.html',
})
export class LearningPathNodeComponent {
    @Input() courseId: number;
    @Input() node: NgxLearningPathNode;

    nodeDetailsData = new NodeDetailsData();

    protected readonly NodeType = NodeType;
    protected readonly getIcon = getIcon;
}
