import { Component, Input } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
@Component({
    selector: 'jhi-learning-path-progress-modal',
    styleUrls: ['./learning-path-progress-modal.component.scss'],
    templateUrl: './learning-path-progress-modal.component.html',
})
export class LearningPathProgressModalComponent {
    @Input() learningPathId: number;
    constructor(private activeModal: NgbActiveModal) {}

    close() {
        this.activeModal.close();
    }
}
