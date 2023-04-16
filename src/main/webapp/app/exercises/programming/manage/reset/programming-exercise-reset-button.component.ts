import { Component, Input } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExerciseResetDialogComponent } from 'app/exercises/programming/manage/reset/programming-exercise-reset-dialog.component';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { faEraser } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

@Component({
    selector: 'jhi-programming-exercise-reset',
    template: `
        <jhi-button
            *ngIf="programmingExercise.isAtLeastInstructor"
            [btnType]="ButtonType.ERROR"
            [btnSize]="ButtonSize.SMALL"
            [shouldSubmit]="false"
            [featureToggle]="FeatureToggle.ProgrammingExercises"
            [icon]="faEraser"
            [title]="'entity.action.reset'"
            (onClick)="openResetDialog($event)"
        ></jhi-button>
    `,
})
export class ProgrammingExerciseResetButtonComponent {
    readonly ButtonType = ButtonType;
    readonly ButtonSize = ButtonSize;
    readonly FeatureToggle = FeatureToggle;

    @Input() programmingExercise: ProgrammingExercise;

    // Icons
    faEraser = faEraser;

    constructor(private modalService: NgbModal) {}

    /**
     * Stops the propagation of the mouse event and updates the component instance
     * of the modalRef with this instance's values
     * @param {MouseEvent} event - Mouse event
     */
    openResetDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef = this.modalService.open(ProgrammingExerciseResetDialogComponent, { keyboard: true, size: 'lg' });
        modalRef.componentInstance.programmingExercise = this.programmingExercise;
    }
}
