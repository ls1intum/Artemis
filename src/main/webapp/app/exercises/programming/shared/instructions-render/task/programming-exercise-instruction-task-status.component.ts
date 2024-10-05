import { Component, Input, inject } from '@angular/core';
import { faCheckCircle, faTimesCircle } from '@fortawesome/free-regular-svg-icons';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { Result } from 'app/entities/result.model';
import { ProgrammingExerciseInstructionService, TestCaseState } from 'app/exercises/programming/shared/instructions-render/service/programming-exercise-instruction.service';
import { FeedbackComponent } from 'app/exercises/shared/feedback/feedback.component';

@Component({
    selector: 'jhi-programming-exercise-instructions-task-status',
    templateUrl: './programming-exercise-instruction-task-status.component.html',
    styleUrls: ['./programming-exercise-instruction-task-status.scss'],
})
export class ProgrammingExerciseInstructionTaskStatusComponent {
    private programmingExerciseInstructionService = inject(ProgrammingExerciseInstructionService);
    private modalService = inject(NgbModal);

    TestCaseState = TestCaseState;
    translationBasePath = 'artemisApp.editor.testStatusLabels.';

    @Input() taskName: string;

    /**
     * array of test ids
     */
    @Input()
    get testIds() {
        return this.testIdsValue;
    }
    @Input() exercise: Exercise;
    @Input() latestResult?: Result;

    testIdsValue: number[];
    testCaseState: TestCaseState;

    /**
     * Arrays of test case ids, grouped by their status in the given result.
     */
    successfulTests: number[];
    notExecutedTests: number[];
    failedTests: number[];

    hasMessage: boolean;

    // Icons
    faQuestionCircle = faQuestionCircle;
    farCheckCircle = faCheckCircle;
    farTimesCircle = faTimesCircle;

    set testIds(testIds: number[]) {
        this.testIdsValue = testIds;
        const {
            testCaseState,
            detailed: { successfulTests, notExecutedTests, failedTests },
        } = this.programmingExerciseInstructionService.testStatusForTask(this.testIds, this.latestResult);
        this.testCaseState = testCaseState;
        this.successfulTests = successfulTests;
        this.notExecutedTests = notExecutedTests;
        this.failedTests = failedTests;
        this.hasMessage = this.hasTestMessage(testIds);
    }

    /**
     * Checks if any of the feedbacks have a detailText associated to them.
     * @param testIds the test case ids that should be checked for
     */
    private hasTestMessage(testIds: number[]): boolean {
        if (!this.latestResult?.feedbacks) {
            return false;
        }
        const feedbacks = this.latestResult.feedbacks;
        return testIds.some((testId: number) => feedbacks.find((feedback) => feedback.testCase?.id === testId && feedback.detailText));
    }

    /**
     * Opens the FeedbackComponent as popup. Displays test results.
     */
    public showDetailsForTests() {
        if (!this.latestResult) {
            return;
        }
        const modalRef = this.modalService.open(FeedbackComponent, { keyboard: true, size: 'lg' });
        const componentInstance = modalRef.componentInstance as FeedbackComponent;
        componentInstance.exercise = this.exercise;
        componentInstance.result = this.latestResult;
        componentInstance.feedbackFilter = this.testIds;
        componentInstance.exerciseType = ExerciseType.PROGRAMMING;
        componentInstance.taskName = this.taskName;
        componentInstance.numberOfNotExecutedTests = this.notExecutedTests.length;
    }
}
