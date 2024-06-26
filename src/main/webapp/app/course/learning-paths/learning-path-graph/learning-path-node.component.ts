import { Component, Input, OnInit } from '@angular/core';
import { CompetencyProgressForLearningPathDTO, NgxLearningPathNode, NodeType, getIcon } from 'app/entities/competency/learning-path.model';
import { Competency, CompetencyProgress, getMastery, getProgress } from 'app/entities/competency.model';
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
    styleUrls: ['./learning-path-graph.component.scss'],
    templateUrl: './learning-path-node.component.html',
})
export class LearningPathNodeComponent implements OnInit {
    @Input() courseId: number;
    @Input() node: NgxLearningPathNode;
    @Input() competencyProgressDTO?: CompetencyProgressForLearningPathDTO;

    nodeDetailsData = new NodeDetailsData();

    protected readonly NodeType = NodeType;
    protected readonly getIcon = getIcon;

    constructor() {}

    ngOnInit() {
        if (this.competencyProgressDTO) {
            this.nodeDetailsData.competencyProgress = { progress: this.competencyProgressDTO.progress, confidence: this.competencyProgressDTO.confidence };
        }
    }

    get progress() {
        return getProgress(this.nodeDetailsData.competencyProgress!);
    }

    get mastery() {
        return getMastery(this.nodeDetailsData.competencyProgress);
    }
}
