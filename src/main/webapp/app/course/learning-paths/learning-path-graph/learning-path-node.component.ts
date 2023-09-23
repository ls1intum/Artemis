import { Component, Input } from '@angular/core';
import { faCheckCircle, faCircle, faFlag, faFlagCheckered, faPlayCircle, faQuestionCircle, faSignsPost } from '@fortawesome/free-solid-svg-icons';
import { NgxLearningPathNode, NodeType } from 'app/entities/competency/learning-path.model';
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

    faCheckCircle = faCheckCircle;
    faPlayCircle = faPlayCircle;
    faQuestionCircle = faQuestionCircle;
    faCircle = faCircle;
    faSignsPost = faSignsPost;
    faFlagCheckered = faFlagCheckered;
    faFlag = faFlag;

    nodeDetailsData = new NodeDetailsData();

    protected readonly NodeType = NodeType;
}
