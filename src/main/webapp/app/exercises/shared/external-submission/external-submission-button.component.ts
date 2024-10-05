import { Component, Input, inject } from '@angular/core';
import { faPlus } from '@fortawesome/free-solid-svg-icons';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Exercise } from 'app/entities/exercise.model';
import { ExternalSubmissionDialogComponent } from 'app/exercises/shared/external-submission/external-submission-dialog.component';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';

@Component({
    selector: 'jhi-external-submission',
    template: `
        @if (!exercise.teamMode) {
            <jhi-button
                [btnType]="ButtonType.WARNING"
                [btnSize]="ButtonSize.SMALL"
                [icon]="faPlus"
                [title]="'entity.action.addExternalSubmission'"
                (onClick)="openExternalSubmissionDialog($event)"
            />
        }
    `,
})
export class ExternalSubmissionButtonComponent {
    private modalService = inject(NgbModal);

    ButtonType = ButtonType;
    ButtonSize = ButtonSize;

    @Input() exercise: Exercise;

    // Icons
    faPlus = faPlus;

    /**
     * Opens modal window for external exercise submission.
     * @param { MouseEvent } event
     */
    openExternalSubmissionDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(ExternalSubmissionDialogComponent, { keyboard: true, size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.exercise = this.exercise;
    }
}
