import { Component, Input } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Exercise } from 'app/entities/exercise.model';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { AuxiliaryRepositoryDialogComponent } from 'app/exercises/programming/manage/update/auxiliary-repository-dialog.component';

@Component({
    selector: 'jhi-add-auxiliary-repository-button',
    template: `
        <jhi-button
            [btnType]="ButtonType.SUCCESS"
            [btnSize]="ButtonSize.SMALL"
            [icon]="'plus'"
            [title]="'entity.action.addAuxiliaryRepository'"
            (onClick)="openExternalSubmissionDialog($event)"
        ></jhi-button>
    `,
})
export class AuxiliaryRepositoryButtonComponent {
    ButtonType = ButtonType;
    ButtonSize = ButtonSize;

    @Input() exercise: Exercise;

    constructor(private modalService: NgbModal) {}

    /**
     * Opens modal window for external exercise submission.
     * @param { MouseEvent } event
     */
    openExternalSubmissionDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(AuxiliaryRepositoryDialogComponent, { keyboard: true, size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.exercise = this.exercise;
    }
}
