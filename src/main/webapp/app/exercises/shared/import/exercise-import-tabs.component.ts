import { Component, Input } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ExerciseType } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-exercise-import-tabs',

    templateUrl: './exercise-import-tabs.component.html',
})
export class ExerciseImportTabsComponent {
    active = 1;
    @Input()
    exerciseType?: ExerciseType;
    @Input()
    courseId?: number;

    constructor(private activeModal: NgbActiveModal) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }
}
