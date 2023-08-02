import { Component, Input, ViewChild } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { LearningPathGraphComponent } from 'app/course/learning-paths/learning-path-graph/learning-path-graph.component';
import { LearningPathPageableSearchDTO } from 'app/entities/competency/learning-path.model';
@Component({
    selector: 'jhi-learning-path-progress-modal',
    styleUrls: ['./learning-path-progress-modal.component.scss'],
    templateUrl: './learning-path-progress-modal.component.html',
})
export class LearningPathProgressModalComponent {
    @Input() courseId: number;
    @Input() learningPath: LearningPathPageableSearchDTO;
    @ViewChild('learningPathGraphComponent') learningPathGraphComponent: LearningPathGraphComponent;

    constructor(private activeModal: NgbActiveModal) {}

    close() {
        this.activeModal.close();
    }
}
