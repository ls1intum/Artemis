import { Component, Input } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { SubmissionExportDialogComponent } from './submission-export-dialog.component';
import { ExerciseType } from 'app/entities/exercise.model';
import { faDownload } from '@fortawesome/free-solid-svg-icons';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';

@Component({
    selector: 'jhi-exercise-submission-export',
    template: `
        <jhi-button
            [featureToggle]="FeatureToggle.Exports"
            [disabled]="!exerciseId"
            [btnType]="ButtonType.INFO"
            [btnSize]="ButtonSize.SMALL"
            [shouldSubmit]="false"
            [icon]="faDownload"
            [title]="'artemisApp.instructorDashboard.exportSubmissions.title'"
            (onClick)="openSubmissionExportDialog($event)"
        ></jhi-button>
    `,
})
export class SubmissionExportButtonComponent {
    ButtonType = ButtonType;
    ButtonSize = ButtonSize;
    readonly FeatureToggle = FeatureToggle;

    @Input() exerciseId: number;
    @Input() exerciseType: ExerciseType;

    // Icons
    faDownload = faDownload;

    constructor(private modalService: NgbModal) {}

    /**
     * Stops the propagation of the mouse event and updates the component instance
     * of the modalRef with this instance's values
     * @param {MouseEvent} event - Mouse event
     */
    openSubmissionExportDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef = this.modalService.open(SubmissionExportDialogComponent, { keyboard: true, size: 'lg' });
        modalRef.componentInstance.exerciseId = this.exerciseId;
        modalRef.componentInstance.exerciseType = this.exerciseType;
    }
}
