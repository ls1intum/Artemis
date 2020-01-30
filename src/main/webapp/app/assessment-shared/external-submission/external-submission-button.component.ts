import { Component, Input } from '@angular/core';
import { ButtonSize, ButtonType } from 'app/shared/components';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Exercise } from 'app/entities/exercise';
import { ExternalSubmissionDialogComponent } from 'app/assessment-shared/external-submission/external-submission-dialog.component';

@Component({
    selector: 'jhi-external-submission',
    template: `
        <jhi-button
            [btnType]="ButtonType.WARNING"
            [btnSize]="ButtonSize.SMALL"
            [icon]="'asterisk'"
            [title]="'entity.action.addExternalSubmission'"
            (onClick)="openExternalSubmissionDialog($event)"
        ></jhi-button>
    `,
})
export class ExternalSubmissionButtonComponent {
    ButtonType = ButtonType;
    ButtonSize = ButtonSize;

    @Input() exercise: Exercise;

    constructor(private modalService: NgbModal) {}

    openExternalSubmissionDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(ExternalSubmissionDialogComponent, { keyboard: true, size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.exercise = this.exercise;
    }
}
