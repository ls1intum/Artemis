import { Component, Input } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { TextSubmissionExportDialogComponent } from './text-submission-export-dialog.component';

@Component({
    selector: 'jhi-text-exercise-submission-export',
    template: `
        <jhi-button
            [disabled]="!exerciseId"
            [btnType]="ButtonType.INFO"
            [btnSize]="ButtonSize.SMALL"
            [shouldSubmit]="false"
            [icon]="'download'"
            [title]="'instructorDashboard.exportSubmissions'"
            (onClick)="openSubmissionExportDialog($event)"
        ></jhi-button>
    `,
})
export class TextSubmissionExportButtonComponent {
    ButtonType = ButtonType;
    ButtonSize = ButtonSize;

    @Input() exerciseId: number;

    constructor(private modalService: NgbModal) {}

    /**
     * Stops the propagation of the mouse event and updates the component instance
     * of the modalRef with this instance's values
     * @param {MouseEvent} event - Mouse event
     */
    openSubmissionExportDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef = this.modalService.open(TextSubmissionExportDialogComponent, { keyboard: true, size: 'lg' });
        modalRef.componentInstance.exerciseId = this.exerciseId;
    }
}
