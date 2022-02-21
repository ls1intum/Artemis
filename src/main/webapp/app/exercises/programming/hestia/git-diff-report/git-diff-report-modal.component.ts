import { Component, Input } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';

@Component({
    selector: 'jhi-git-diff-report-modal',
    templateUrl: './git-diff-report-modal.component.html',
})
export class GitDiffReportModalComponent {
    @Input()
    report: ProgrammingExerciseGitDiffReport;

    constructor(protected activeModal: NgbActiveModal) {}

    close(): void {
        this.activeModal.dismiss();
    }
}
