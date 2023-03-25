import { Component, Input } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ExerciseType } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-exercise-import-wrapper',
    templateUrl: './exercise-import-wrapper.component.html',
})
export class ExerciseImportWrapperComponent {
    readonly ExerciseType = ExerciseType;

    @Input()
    exerciseType: ExerciseType;

    constructor(private activeModal: NgbActiveModal) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }
}
