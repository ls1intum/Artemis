import { Component, Input } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Result } from 'app/entities/result';
import { ProgrammingExerciseService, TestCaseState } from 'app/entities/programming-exercise';
import { ProgrammingExerciseInstructionResultDetailComponent } from 'app/entities/programming-exercise/instructions/programming-exercise-instructions-result-detail.component';
import { ProgrammingExerciseInstructionService } from 'app/entities/programming-exercise/instructions/programming-exercise-instruction.service';

@Component({
    selector: 'jhi-programming-exercise-instructions-step-wizard',
    templateUrl: './programming-exercise-instruction-step-wizard.component.html',
    styleUrls: ['./programming-exercise-instruction-step-wizard.scss'],
})
export class ProgrammingExerciseInstructionStepWizardComponent {
    TestCaseState = TestCaseState;
    @Input() latestResult: Result | null;
    @Input() steps: Array<{ done: TestCaseState; title: string; tests: string[] }>;

    constructor(private modalService: NgbModal, private instructionService: ProgrammingExerciseInstructionService) {}

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
        const {
            detailed: { failedTests, notExecutedTests },
        } = this.instructionService.testStatusForTask(tests, this.latestResult);
        if (failedTests.length + notExecutedTests.length > 0) {
            return;
        }
        const modalRef = this.modalService.open(ProgrammingExerciseInstructionResultDetailComponent, { keyboard: true, size: 'lg' });
        modalRef.componentInstance.result = this.latestResult;
        modalRef.componentInstance.tests = tests;
    }
}
