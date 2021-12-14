import { Component, Input } from '@angular/core';
import { faAsterisk } from '@fortawesome/free-solid-svg-icons';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Exercise } from 'app/entities/exercise.model';
import { ExternalSubmissionDialogComponent } from 'app/exercises/shared/external-submission/external-submission-dialog.component';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';

@Component({
    selector: 'jhi-external-submission',
    template: `
        <jhi-button
            *ngIf="!exercise.teamMode"
            [btnType]="ButtonType.WARNING"
            [btnSize]="ButtonSize.SMALL"
            [icon]="faAsterisk"
            [title]="'entity.action.addExternalSubmission'"
            (onClick)="openExternalSubmissionDialog($event)"
        ></jhi-button>
    `,
})
export class ExternalSubmissionButtonComponent {
    ButtonType = ButtonType;
    ButtonSize = ButtonSize;

    @Input() exercise: Exercise;

    // Icons
    faAsterisk = faAsterisk;

    constructor(private modalService: NgbModal) {}

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
