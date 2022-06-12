import { Component, EventEmitter, Input, Output } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ExerciseHint } from 'app/entities/hestia/exercise-hint.model';
import { faCircleQuestion } from '@fortawesome/free-solid-svg-icons';
import { ExerciseHintStudentDialogComponent } from 'app/exercises/shared/exercise-hint/participate/exercise-hint-student-dialog.component';
import cloneDeep from 'lodash-es/cloneDeep';

@Component({
    selector: 'jhi-exercise-hint-button-overlay',
    templateUrl: './exercise-hint-button-overlay.component.html',
    styleUrls: ['./exercise-hint-button-overlay.component.scss'],
})
export class ExerciseHintButtonOverlayComponent {
    @Input()
    availableExerciseHints?: ExerciseHint[];
    @Input()
    activatedExerciseHints?: ExerciseHint[];
    @Output()
    onHintActivated = new EventEmitter<ExerciseHint>();

    faCircleQuestion = faCircleQuestion;
    ngbModalRef?: NgbModalRef;

    constructor(private modalService: NgbModal) {}

    openModal() {
        this.ngbModalRef = this.modalService.open(ExerciseHintStudentDialogComponent as Component, { size: 'lg', backdrop: 'static' });
        this.ngbModalRef.componentInstance.onHintActivated = this.onHintActivated;
        // cloning is required that the lists not change while modal is open
        this.ngbModalRef.componentInstance.activatedExerciseHints = cloneDeep(this.activatedExerciseHints!);
        this.ngbModalRef.componentInstance.availableExerciseHints = cloneDeep(this.availableExerciseHints!);
        this.ngbModalRef.result.then(
            () => {
                this.ngbModalRef = undefined;
            },
            () => {
                this.ngbModalRef = undefined;
            },
        );
    }
}
