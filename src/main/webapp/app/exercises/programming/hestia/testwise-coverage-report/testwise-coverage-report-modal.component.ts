import { Component, Input } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExerciseTestwiseCoverageReport } from 'app/entities/hestia/programming-exercise-testwise-coverage-report.model';

@Component({
    selector: 'jhi-testwise-coverage-report-modal',
    templateUrl: './testwise-coverage-report-modal.component.html',
})
export class TestwiseCoverageReportModalComponent {
    @Input()
    reports: ProgrammingExerciseTestwiseCoverageReport[];

    @Input()
    fileContentByPath: Map<string, string>;

    constructor(protected activeModal: NgbActiveModal) {}

    close(): void {
        this.activeModal.dismiss();
    }
}
