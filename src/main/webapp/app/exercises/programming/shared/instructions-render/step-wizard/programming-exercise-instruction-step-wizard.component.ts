import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExerciseInstructionService, TestCaseState } from 'app/exercises/programming/shared/instructions-render/service/programming-exercise-instruction.service';
import { TaskArray } from 'app/exercises/programming/shared/instructions-render/task/programming-exercise-task.model';
import { ResultDetailComponent } from 'app/shared/result/result-detail.component';
import { ExerciseType } from 'app/entities/exercise.model';
import { Result } from 'app/entities/result.model';

@Component({
    selector: 'jhi-programming-exercise-instructions-step-wizard',
    templateUrl: './programming-exercise-instruction-step-wizard.component.html',
    styleUrls: ['./programming-exercise-instruction-step-wizard.scss'],
})
export class ProgrammingExerciseInstructionStepWizardComponent implements OnChanges {
    TestCaseState = TestCaseState;

    @Input() latestResult: Result | null;
    @Input() tasks: TaskArray;

    steps: Array<{ done: TestCaseState; title: string; tests: string[] }>;

    constructor(private modalService: NgbModal, private instructionService: ProgrammingExerciseInstructionService) {}

    ngOnChanges(changes: SimpleChanges): void {
        if ((changes.tasks && this.tasks) || (this.tasks && changes.latestResult)) {
            this.steps = this.tasks.map(({ taskName, tests }) => ({
                done: this.instructionService.testStatusForTask(tests, this.latestResult).testCaseState,
                title: taskName,
                tests,
            }));
        }
    }

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
        if (failedTests.length + notExecutedTests.length <= 0) {
            return;
        }
        const modalRef = this.modalService.open(ResultDetailComponent, { keyboard: true, size: 'lg' });
        modalRef.componentInstance.result = this.latestResult;
        modalRef.componentInstance.feedbackFilter = tests;
        modalRef.componentInstance.exerciseType = ExerciseType.PROGRAMMING;
    }
}
