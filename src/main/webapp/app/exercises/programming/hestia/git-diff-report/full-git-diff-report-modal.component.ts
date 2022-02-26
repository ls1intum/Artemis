import { Component, Input } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExerciseFullGitDiffReport } from 'app/entities/hestia/programming-exercise-full-git-diff-report.model';

@Component({
    selector: 'jhi-git-diff-report-modal',
    templateUrl: './full-git-diff-report-modal.component.html',
})
export class FullGitDiffReportModalComponent {
    @Input()
    report: ProgrammingExerciseFullGitDiffReport;

    constructor(protected activeModal: NgbActiveModal) {}

    close(): void {
        this.activeModal.dismiss();
    }
}
