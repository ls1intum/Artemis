import { Component, Input } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingAssessmentRepoExportDialogComponent } from 'app/exercises/programming/assess/repo-export/programming-assessment-repo-export-dialog.component';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { faDownload } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-programming-assessment-repo-export',
    template: `
        <jhi-button
            [disabled]="!exerciseId"
            [btnType]="ButtonType.INFO"
            [btnSize]="ButtonSize.SMALL"
            [shouldSubmit]="false"
            [featureToggle]="[FeatureToggle.ProgrammingExercises, FeatureToggle.Exports]"
            [icon]="faDownload"
            [title]="singleParticipantMode ? 'instructorDashboard.exportRepos.titleSingle' : 'instructorDashboard.exportRepos.title'"
            (onClick)="openRepoExportDialog($event)"
        ></jhi-button>
    `,
})
export class ProgrammingAssessmentRepoExportButtonComponent {
    ButtonType = ButtonType;
    ButtonSize = ButtonSize;
    readonly FeatureToggle = FeatureToggle;

    @Input() exerciseId: number;
    @Input() participationIdList: number[];
    @Input() participantIdentifierList: string; // comma separated
    @Input() singleParticipantMode = false;

    // Icons
    faDownload = faDownload;

    constructor(private modalService: NgbModal) {}

    /**
     * Stops the propagation of the mouse event and updates the component instance
     * of the modalRef with this instance's values
     * @param {MouseEvent} event - Mouse event
     */
    openRepoExportDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef = this.modalService.open(ProgrammingAssessmentRepoExportDialogComponent, { keyboard: true, size: 'lg' });
        modalRef.componentInstance.exerciseId = this.exerciseId;
        modalRef.componentInstance.participationIdList = this.participationIdList;
        modalRef.componentInstance.participantIdentifierList = this.participantIdentifierList;
        modalRef.componentInstance.singleParticipantMode = this.singleParticipantMode;
    }
}
