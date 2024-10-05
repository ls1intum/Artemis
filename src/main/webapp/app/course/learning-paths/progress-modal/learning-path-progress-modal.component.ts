import { Component, Input, ViewChild, inject } from '@angular/core';
import { Router } from '@angular/router';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { LearningPathGraphComponent } from 'app/course/learning-paths/learning-path-graph/learning-path-graph.component';
import { LearningPathInformationDTO, NgxLearningPathNode, NodeType } from 'app/entities/competency/learning-path.model';

@Component({
    selector: 'jhi-learning-path-progress-modal',
    styleUrls: ['./learning-path-progress-modal.component.scss'],
    templateUrl: './learning-path-progress-modal.component.html',
})
export class LearningPathProgressModalComponent {
    private activeModal = inject(NgbActiveModal);
    private router = inject(Router);

    @Input() courseId: number;
    @Input() learningPath: LearningPathInformationDTO;
    @ViewChild('learningPathGraphComponent') learningPathGraphComponent: LearningPathGraphComponent;

    close() {
        this.activeModal.close();
    }

    onNodeClicked(node: NgxLearningPathNode) {
        if (node.type === NodeType.COMPETENCY_START || node.type === NodeType.COMPETENCY_END) {
            this.navigateToCompetency(node);
        }
    }

    navigateToCompetency(node: NgxLearningPathNode) {
        this.router.navigate(['courses', this.courseId, 'competencies', node.linkedResource]);
        this.close();
    }
}
