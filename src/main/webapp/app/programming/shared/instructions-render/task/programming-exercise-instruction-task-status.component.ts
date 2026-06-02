import { Component, computed, inject, input } from '@angular/core';
import { faCheckCircle, faCircleDot, faTimesCircle } from '@fortawesome/free-regular-svg-icons';
import { DialogService } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ProgrammingExerciseInstructionService, TestCaseState } from 'app/programming/shared/instructions-render/services/programming-exercise-instruction.service';
import { FeedbackComponent } from 'app/exercise/feedback/feedback.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { SafeHtmlPipe } from 'app/foundation/pipes/safe-html.pipe';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';

@Component({
    selector: 'jhi-programming-exercise-instructions-task-status',
    templateUrl: './programming-exercise-instruction-task-status.component.html',
    styleUrls: ['./programming-exercise-instruction-task-status.scss'],
    imports: [FaIconComponent, ArtemisTranslatePipe, SafeHtmlPipe],
})
export class ProgrammingExerciseInstructionTaskStatusComponent {
    private programmingExerciseInstructionService = inject(ProgrammingExerciseInstructionService);
    private dialogService = inject(DialogService);
    private translateService = inject(TranslateService);

    TestCaseState = TestCaseState;
    translationBasePath = 'artemisApp.editor.testStatusLabels.';

    readonly taskName = input.required<string>();
    readonly testIds = input<number[]>([]);
    readonly exercise = input.required<Exercise>();
    readonly latestResult = input<Result | undefined>(undefined);
    readonly participation = input.required<Participation>();

    private readonly testStatus = computed(() => this.programmingExerciseInstructionService.testStatusForTask(this.testIds() ?? [], this.latestResult()));
    readonly testCaseState = computed(() => this.testStatus().testCaseState);
    readonly successfulTests = computed(() => this.testStatus().detailed.successfulTests);
    readonly notExecutedTests = computed(() => this.testStatus().detailed.notExecutedTests);
    readonly failedTests = computed(() => this.testStatus().detailed.failedTests);
    readonly hasMessage = computed(() => this.computeHasTestMessage(this.testIds() ?? []));

    // Icons
    faCircleDot = faCircleDot;
    farCheckCircle = faCheckCircle;
    farTimesCircle = faTimesCircle;

    /**
     * Checks if any of the feedbacks have a detailText associated to them.
     * @param testIds the test case ids that should be checked for
     */
    private computeHasTestMessage(testIds: number[]): boolean {
        const latestResult = this.latestResult();
        if (!latestResult?.feedbacks) {
            return false;
        }
        const feedbacks = latestResult.feedbacks;
        return testIds.some((testId: number) => feedbacks.find((feedback) => feedback.testCase?.id === testId && feedback.detailText));
    }

    /**
     * Opens the FeedbackComponent as popup. Displays test results.
     */
    showDetailsForTests() {
        const latestResult = this.latestResult();
        if (!latestResult) {
            return;
        }
        this.dialogService.open(FeedbackComponent, {
            header: this.translateService.instant('artemisApp.result.detail.feedbackForTask', { taskName: this.taskName() }),
            width: '50rem',
            modal: true,
            closable: true,
            closeOnEscape: true,
            dismissableMask: true,
            data: {
                exercise: this.exercise(),
                result: latestResult,
                participation: this.participation(),
                feedbackFilter: this.testIds(),
                exerciseType: ExerciseType.PROGRAMMING,
                taskName: this.taskName(),
                numberOfNotExecutedTests: this.notExecutedTests().length,
            },
        });
    }
}
