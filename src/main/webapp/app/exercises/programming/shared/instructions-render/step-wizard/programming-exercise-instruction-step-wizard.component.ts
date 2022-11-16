import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExerciseInstructionService, TestCaseState } from 'app/exercises/programming/shared/instructions-render/service/programming-exercise-instruction.service';
import { TaskArray } from 'app/exercises/programming/shared/instructions-render/task/programming-exercise-task.model';
import { ResultDetailComponent } from 'app/exercises/shared/result/result-detail.component';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { Result } from 'app/entities/result.model';
import { faCheck, faQuestion, faTimes } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-programming-exercise-instructions-step-wizard',
    templateUrl: './programming-exercise-instruction-step-wizard.component.html',
    styleUrls: ['./programming-exercise-instruction-step-wizard.scss'],
})
export class ProgrammingExerciseInstructionStepWizardComponent implements OnChanges {
    TestCaseState = TestCaseState;

    @Input() exercise: Exercise;
    @Input() latestResult?: Result;
    @Input() tasks: TaskArray;
    @Input() showTestDetails?: boolean;

    steps: Array<{ done: TestCaseState; title: string; tests: string[] }>;

    // Icons
    faTimes = faTimes;
    faCheck = faCheck;
    faQuestion = faQuestion;

    constructor(private modalService: NgbModal, private instructionService: ProgrammingExerciseInstructionService) {}

    /**
     * Life cycle hook called by Angular to indicate that changes are detected.
     * @param changes - change that is detected.
     */
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
     * Opens the ResultDetailComponent as popup; displays test results
     * @param {string[]} tests - Identifies the testcase
     * @param taskName - the name of the selected task
     */
    public showDetailsForTests(tests: string[], taskName: string) {
        if (!this.latestResult) {
            return;
        }
        const {
            detailed: { notExecutedTests },
        } = this.instructionService.testStatusForTask(tests, this.latestResult);
        const modalRef = this.modalService.open(ResultDetailComponent, { keyboard: true, size: 'lg' });
        const componentInstance = modalRef.componentInstance as ResultDetailComponent;
        componentInstance.exercise = this.exercise;
        componentInstance.result = this.latestResult;
        componentInstance.feedbackFilter = tests;
        componentInstance.exerciseType = ExerciseType.PROGRAMMING;
        componentInstance.showTestDetails = this.showTestDetails || false;
        componentInstance.taskName = taskName;
        componentInstance.numberOfNotExecutedTests = notExecutedTests.length;
    }
}
