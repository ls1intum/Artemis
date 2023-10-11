import { Component, Input, OnInit } from '@angular/core';
import { NgxLearningPathNode, NodeType, getIcon } from 'app/entities/competency/learning-path.model';
import { Competency, CompetencyProgress, getConfidence, getMastery, getProgress } from 'app/entities/competency.model';
import { Exercise } from 'app/entities/exercise.model';
import { Lecture } from 'app/entities/lecture.model';
import { LectureUnitForLearningPathNodeDetailsDTO } from 'app/entities/lecture-unit/lectureUnit.model';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { HttpErrorResponse } from '@angular/common/http';

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

    nodeDetailsData = new NodeDetailsData();

    protected readonly NodeType = NodeType;
    protected readonly getIcon = getIcon;

    constructor(
        private competencyService: CompetencyService,
        private alertService: AlertService,
    ) {}

    ngOnInit() {
        if (this.node?.type === NodeType.COMPETENCY_START) {
            this.loadCompetencyProgress();
        }
    }

    loadCompetencyProgress() {
        this.competencyService.findById(this.node.linkedResource!, this.courseId!).subscribe({
            next: (resp) => {
                this.nodeDetailsData.competency = resp.body!;
                if (this.nodeDetailsData.competency!.userProgress?.length) {
                    this.nodeDetailsData.competencyProgress = this.nodeDetailsData.competency!.userProgress.first()!;
                } else {
                    console.log('else');
                    this.nodeDetailsData.competencyProgress = { progress: 0, confidence: 0 } as CompetencyProgress;
                }
            },
            error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
        });
    }

    get progress() {
        return getProgress(this.nodeDetailsData.competencyProgress!);
    }

    get confidence() {
        return getConfidence(this.nodeDetailsData.competency!, this.nodeDetailsData.competencyProgress!);
    }

    get mastery() {
        return getMastery(this.nodeDetailsData.competency!, this.nodeDetailsData.competencyProgress!);
    }
}
