import { Component, Input, OnChanges, SimpleChanges, inject } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExerciseInstructionService, TestCaseState } from 'app/exercises/programming/shared/instructions-render/service/programming-exercise-instruction.service';
import { TaskArray } from 'app/exercises/programming/shared/instructions-render/task/programming-exercise-task.model';
import { FeedbackComponent } from 'app/exercises/shared/feedback/feedback.component';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { Result } from 'app/entities/result.model';
import { faCheck, faQuestion, faTimes } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-programming-exercise-instructions-step-wizard',
    templateUrl: './programming-exercise-instruction-step-wizard.component.html',
    styleUrls: ['./programming-exercise-instruction-step-wizard.scss'],
})
export class ProgrammingExerciseInstructionStepWizardComponent implements OnChanges {
    private modalService = inject(NgbModal);
    private instructionService = inject(ProgrammingExerciseInstructionService);

    TestCaseState = TestCaseState;

    @Input() exercise: Exercise;
    @Input() latestResult?: Result;
    @Input() tasks: TaskArray;

    steps: Array<{ done: TestCaseState; title: string; testIds: number[] }>;

    // Icons
    faTimes = faTimes;
    faCheck = faCheck;
    faQuestion = faQuestion;

    /**
     * Life cycle hook called by Angular to indicate that changes are detected.
     * @param changes - change that is detected.
     */
    ngOnChanges(changes: SimpleChanges): void {
        if ((changes.tasks && this.tasks) || (this.tasks && changes.latestResult)) {
            this.steps = this.tasks.map(({ taskName, testIds }) => ({
                done: this.instructionService.testStatusForTask(testIds, this.latestResult).testCaseState,
                title: taskName,
                testIds,
            }));
        }
    }

    /**
     * Opens the FeedbackComponent as popup; displays test results
     * @param {string[]} tests - Identifies the testcase
     * @param taskName - the name of the selected task
     */
    public showDetailsForTests(tests: number[], taskName: string) {
        if (!this.latestResult || !tests.length) {
            return;
        }
        const {
            detailed: { notExecutedTests },
        } = this.instructionService.testStatusForTask(tests, this.latestResult);
        const modalRef = this.modalService.open(FeedbackComponent, { keyboard: true, size: 'lg' });
        const componentInstance = modalRef.componentInstance as FeedbackComponent;
        componentInstance.exercise = this.exercise;
        componentInstance.result = this.latestResult;
        componentInstance.feedbackFilter = tests;
        componentInstance.exerciseType = ExerciseType.PROGRAMMING;
        componentInstance.taskName = taskName;
        componentInstance.numberOfNotExecutedTests = notExecutedTests.length;
    }
}
