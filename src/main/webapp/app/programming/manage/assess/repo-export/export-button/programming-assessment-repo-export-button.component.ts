import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingAssessmentRepoExportDialogComponent } from 'app/programming/manage/assess/repo-export/export-dialog/programming-assessment-repo-export-dialog.component';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { faDownload } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';

@Component({
    selector: 'jhi-programming-assessment-repo-export',
    template: `
        <jhi-button
            [disabled]="!programmingExercises"
            [btnType]="ButtonType.INFO"
            [btnSize]="ButtonSize.SMALL"
            [shouldSubmit]="false"
            [featureToggle]="[FeatureToggle.ProgrammingExercises, FeatureToggle.Exports]"
            [icon]="faDownload"
            [title]="singleParticipantMode ? 'artemisApp.instructorDashboard.exportRepos.titleSingle' : 'artemisApp.instructorDashboard.exportRepos.title'"
            (onClick)="openRepoExportDialog($event)"
        />
    `,
    imports: [ButtonComponent],
})
export class ProgrammingAssessmentRepoExportButtonComponent {
    private modalService = inject(NgbModal);

    readonly ButtonType = ButtonType;
    readonly ButtonSize = ButtonSize;
    readonly FeatureToggle = FeatureToggle;

    @Input() participationIdList: number[];
    @Input() participantIdentifierList: string; // comma separated
    @Input() singleParticipantMode = false;
    @Input() programmingExercises: ProgrammingExercise[];

    @Output() buttonPressed = new EventEmitter<void>();

    // Icons
    faDownload = faDownload;

    /**
     * Stops the propagation of the mouse event and updates the component instance
     * of the modalRef with this instance's values
     * @param {MouseEvent} event - Mouse event
     */
    openRepoExportDialog(event: MouseEvent) {
        this.buttonPressed.emit();
        event.stopPropagation();
        const modalRef = this.modalService.open(ProgrammingAssessmentRepoExportDialogComponent, { keyboard: true, size: 'lg' });
        modalRef.componentInstance.programmingExercises = this.programmingExercises;
        modalRef.componentInstance.participationIdList = this.participationIdList;
        modalRef.componentInstance.participantIdentifierList = this.participantIdentifierList;
        modalRef.componentInstance.singleParticipantMode = this.singleParticipantMode;
    }
}
