import { Component, effect, inject, input, signal } from '@angular/core';
import { NgbModal, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExerciseInstructionService, TestCaseState } from 'app/programming/shared/instructions-render/services/programming-exercise-instruction.service';
import { TaskArray } from 'app/programming/shared/instructions-render/task/programming-exercise-task.model';
import { FeedbackComponent } from 'app/exercise/feedback/feedback.component';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { faCheck, faCircle, faTimes } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';

interface StepWizardStep {
    done: TestCaseState;
    title: string;
    testIds: number[];
}

@Component({
    selector: 'jhi-programming-exercise-instructions-step-wizard',
    templateUrl: './programming-exercise-instruction-step-wizard.component.html',
    styleUrls: ['./programming-exercise-instruction-step-wizard.scss'],
    imports: [TranslateDirective, NgbTooltip, FaIconComponent],
})
export class ProgrammingExerciseInstructionStepWizardComponent {
    // NgbModal is retained here intentionally: the only modal opened is FeedbackComponent, which is
    // a shared component still implemented against NgbActiveModal. Migrating FeedbackComponent to
    // PrimeNG DialogService would expand this PR's scope across multiple unrelated modules.
    // See the cluster-6 scope boundary note for the rationale.
    private modalService = inject(NgbModal);
    private instructionService = inject(ProgrammingExerciseInstructionService);

    TestCaseState = TestCaseState;

    readonly exercise = input<Exercise>(undefined!);
    readonly participation = input<Participation>(undefined!);
    readonly latestResult = input<Result>();
    readonly tasks = input<TaskArray>(undefined!);

    // Internal signal so the template re-renders when the derived steps change. Recomputed by an
    // effect that tracks both `tasks` and `latestResult` — preserving the legacy ngOnChanges
    // "(tasks && changes.tasks) || (tasks && changes.latestResult)" gate.
    readonly steps = signal<StepWizardStep[]>([]);

    // Icons
    faTimes = faTimes;
    faCheck = faCheck;
    faCircle = faCircle;

    constructor() {
        effect(() => {
            const tasks = this.tasks();
            const latestResult = this.latestResult();
            if (tasks) {
                this.steps.set(
                    tasks.map(({ taskName, testIds }) => ({
                        done: this.instructionService.testStatusForTask(testIds, latestResult).testCaseState,
                        title: taskName,
                        testIds,
                    })),
                );
            }
        });
    }

    /**
     * Opens the FeedbackComponent as popup; displays test results
     * @param {string[]} tests - Identifies the testcase
     * @param taskName - the name of the selected task
     */
    public showDetailsForTests(tests: number[], taskName: string) {
        const latestResult = this.latestResult();
        if (!latestResult || !tests.length) {
            return;
        }
        const {
            detailed: { notExecutedTests },
        } = this.instructionService.testStatusForTask(tests, latestResult);
        const modalRef = this.modalService.open(FeedbackComponent, { keyboard: true, size: 'lg' });
        const componentInstance = modalRef.componentInstance as FeedbackComponent;
        componentInstance.exercise = this.exercise();
        componentInstance.result = latestResult;
        componentInstance.participation = this.participation();
        componentInstance.feedbackFilter = tests;
        componentInstance.exerciseType = ExerciseType.PROGRAMMING;
        componentInstance.taskName = taskName;
        componentInstance.numberOfNotExecutedTests = notExecutedTests.length;
    }
}
