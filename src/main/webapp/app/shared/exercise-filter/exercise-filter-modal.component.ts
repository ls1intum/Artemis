import { Component } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-exercise-filter-modal',
    templateUrl: './exercise-filter-modal.component.html',
    // styleUrls: ['./exercise-filter.component.scss'],
    standalone: true,
})
export class ExerciseFilterModalComponent {
    constructor(private activeModal: NgbActiveModal) {}

    clear(): void {
        this.activeModal.close();
    }
}
