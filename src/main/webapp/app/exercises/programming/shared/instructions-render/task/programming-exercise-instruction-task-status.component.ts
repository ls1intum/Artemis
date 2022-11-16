import { ApplicationRef, Component, Input } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExerciseInstructionService, TestCaseState } from 'app/exercises/programming/shared/instructions-render/service/programming-exercise-instruction.service';
import { ResultDetailComponent } from 'app/exercises/shared/result/result-detail.component';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { Result } from 'app/entities/result.model';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { faCheckCircle, faTimesCircle } from '@fortawesome/free-regular-svg-icons';

@Component({
    selector: 'jhi-programming-exercise-instructions-task-status',
    templateUrl: './programming-exercise-instruction-task-status.component.html',
    styleUrls: ['./programming-exercise-instruction-task-status.scss'],
})
export class ProgrammingExerciseInstructionTaskStatusComponent {
    TestCaseState = TestCaseState;
    translationBasePath = 'artemisApp.editor.testStatusLabels.';

    @Input() taskName: string;
    @Input()
    get tests() {
        return this.testsValue;
    }
    @Input() exercise: Exercise;
    @Input() latestResult?: Result;
    @Input() showTestDetails: boolean;

    ngbModalRef?: NgbModalRef;

    testsValue: string[];
    testCaseState: TestCaseState;

    successfulTests: string[];
    notExecutedTests: string[];
    failedTests: string[];

    hasMessage: boolean;

    // Icons
    faQuestionCircle = faQuestionCircle;
    farCheckCircle = faCheckCircle;
    farTimesCircle = faTimesCircle;

    constructor(private programmingExerciseInstructionService: ProgrammingExerciseInstructionService, private appRef: ApplicationRef, private modalService: NgbModal) {}

    set tests(tests: string[]) {
        this.testsValue = tests;
        const {
            testCaseState,
            detailed: { successfulTests, notExecutedTests, failedTests },
        } = this.programmingExerciseInstructionService.testStatusForTask(this.tests, this.latestResult);
        this.testCaseState = testCaseState;
        this.successfulTests = successfulTests;
        this.notExecutedTests = notExecutedTests;
        this.failedTests = failedTests;
        this.hasMessage = this.hasTestMessage(tests);
    }

    /**
     * Checks if any of the feedbacks have a detailText associated to them.
     * @param tests the feedback names this should be checked for
     * @private
     */
    private hasTestMessage(tests: string[]): boolean {
        if (!this.latestResult || !this.latestResult.feedbacks) {
            return false;
        }
        const feedbacks = this.latestResult.feedbacks;
        return tests.some((test) => feedbacks.find((feedback) => feedback.text === test && feedback.detailText));
    }

    /**
     * Opens the ResultDetailComponent as popup. Displays test results.
     */
    public showDetailsForTests() {
        if (!this.latestResult) {
            return;
        }
        const modalRef = this.modalService.open(ResultDetailComponent, { keyboard: true, size: 'lg' });
        const componentInstance = modalRef.componentInstance as ResultDetailComponent;
        componentInstance.exercise = this.exercise;
        componentInstance.result = this.latestResult;
        componentInstance.feedbackFilter = this.tests;
        componentInstance.exerciseType = ExerciseType.PROGRAMMING;
        componentInstance.showTestDetails = this.showTestDetails;
        componentInstance.taskName = this.taskName;
        componentInstance.numberOfNotExecutedTests = this.notExecutedTests.length;
    }
}
