import { Component, Input } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExerciseResetDialogComponent } from 'app/exercises/programming/manage/reset/programming-exercise-reset-dialog.component';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { faTimes } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

@Component({
    selector: 'jhi-programming-exercise-reset',
    template: `
        <button
            *ngIf="programmingExercise.isAtLeastInstructor"
            [jhiFeatureToggle]="FeatureToggle.ProgrammingExercises"
            class="btn btn-danger btn-sm"
            style="display: flex; justify-content: center; align-items: center;"
            (click)="openResetDialog($event)"
            [ngClass]="{ 'd-inline-flex': !fullWidth }"
            [ngStyle]="{
                width: fullWidth ? '100%' : '',
                'border-top-left-radius': noRoundedCornersTop ? '0' : '',
                'border-top-right-radius': noRoundedCornersTop ? '0' : '',
                'border-bottom-left-radius': noRoundedCornersBottom ? '0' : '',
                'border-bottom-right-radius': noRoundedCornersBottom ? '0' : ''
            }"
        >
            <div>
                <fa-icon [icon]="faTimes"></fa-icon>
                <span jhiTranslate="entity.action.reset">Reset</span>
            </div>
        </button>
    `,
})
export class ProgrammingExerciseResetButtonComponent {
    readonly ButtonType = ButtonType;
    readonly ButtonSize = ButtonSize;
    readonly FeatureToggle = FeatureToggle;

    @Input() programmingExercise: ProgrammingExercise;
    @Input() noRoundedCornersTop = false;
    @Input() noRoundedCornersBottom = false;
    @Input() fullWidth = false;

    // Icons
    faTimes = faTimes;

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
