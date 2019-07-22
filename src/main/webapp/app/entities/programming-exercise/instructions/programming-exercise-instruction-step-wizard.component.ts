import { Component, Input } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Result } from 'app/entities/result';
import { TestCaseState } from 'app/entities/programming-exercise';
import { ProgrammingExerciseInstructionResultDetailComponent } from 'app/entities/programming-exercise/instructions/programming-exercise-instructions-result-detail.component';

@Component({
    selector: 'jhi-programming-exercise-instructions-step-wizard',
    templateUrl: './programming-exercise-instruction-step-wizard.component.html',
    styleUrls: ['./programming-exercise-instruction-step-wizard.scss'],
})
export class ProgrammingExerciseInstructionStepWizardComponent {
    TestCaseState = TestCaseState;
    @Input() latestResult: Result | null;
    @Input() steps: Array<{ done: TestCaseState; title: string; tests: string[] }>;

    constructor(private modalService: NgbModal) {}

    /**
     * @function showDetailsForTests
     * @desc Opens the ResultDetailComponent as popup; displays test results
     * @param result {Result} Result object, mostly latestResult
     * @param tests {string} Identifies the testcase
     */
    public showDetailsForTests(tests: string[]) {
        if (!this.latestResult || !this.latestResult.feedbacks) {
            return;
        }
        const anyTestHasNotPassedForTask = tests.some(name => {
            const matchingFeedback = !!this.latestResult && this.latestResult.feedbacks.find(({ text }) => text === name);
            return !matchingFeedback || !matchingFeedback.positive;
        });
        if (!anyTestHasNotPassedForTask) {
            return;
        }
        const modalRef = this.modalService.open(ProgrammingExerciseInstructionResultDetailComponent, { keyboard: true, size: 'lg' });
        modalRef.componentInstance.result = this.latestResult;
        modalRef.componentInstance.tests = tests;
    }
}
