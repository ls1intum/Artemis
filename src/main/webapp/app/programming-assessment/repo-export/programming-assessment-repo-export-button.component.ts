import { Component, Input } from '@angular/core';
import { ButtonSize, ButtonType } from 'app/shared/components';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingAssessmentRepoExportDialogComponent } from 'app/programming-assessment/repo-export/programming-assessment-repo-export-dialog.component';
import { FeatureToggle } from 'app/feature-toggle';

@Component({
    selector: 'jhi-programming-assessment-repo-export',
    template: `
        <jhi-button
            [disabled]="!exerciseId"
            [btnType]="ButtonType.INFO"
            [btnSize]="ButtonSize.SMALL"
            [shouldSubmit]="false"
            [featureToggle]="FeatureToggle.PROGRAMMING_EXERCISES"
            [icon]="'download'"
            [title]="'instructorDashboard.exportRepos.title'"
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
    @Input() studentIdList: string; // comma separated
    @Input() singleStudentMode = false;

    constructor(private modalService: NgbModal) {}

    openRepoExportDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef = this.modalService.open(ProgrammingAssessmentRepoExportDialogComponent, { keyboard: true, size: 'lg' });
        modalRef.componentInstance.exerciseId = this.exerciseId;
        modalRef.componentInstance.participationIdList = this.participationIdList;
        modalRef.componentInstance.studentIdList = this.studentIdList;
        modalRef.componentInstance.singleStudentMode = this.singleStudentMode;
    }
}
