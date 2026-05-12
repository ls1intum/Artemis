import { Component, Input, OnChanges, SimpleChanges, inject } from '@angular/core';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { DialogService } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { ProgrammingExerciseInstructionService, TestCaseState } from 'app/programming/shared/instructions-render/services/programming-exercise-instruction.service';
import { TaskArray } from 'app/programming/shared/instructions-render/task/programming-exercise-task.model';
import { FeedbackComponent } from 'app/exercise/feedback/feedback.component';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { faCheck, faCircle, faTimes } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';

@Component({
    selector: 'jhi-programming-exercise-instructions-step-wizard',
    templateUrl: './programming-exercise-instruction-step-wizard.component.html',
    styleUrls: ['./programming-exercise-instruction-step-wizard.scss'],
    imports: [TranslateDirective, NgbTooltip, FaIconComponent],
})
export class ProgrammingExerciseInstructionStepWizardComponent implements OnChanges {
    private dialogService = inject(DialogService);
    private translateService = inject(TranslateService);
    private instructionService = inject(ProgrammingExerciseInstructionService);

    TestCaseState = TestCaseState;

    @Input() exercise: Exercise;
    @Input() participation: Participation;
    @Input() latestResult?: Result;
    @Input() tasks: TaskArray;

    steps: Array<{ done: TestCaseState; title: string; testIds: number[] }>;

    // Icons
    faTimes = faTimes;
    faCheck = faCheck;
    faCircle = faCircle;

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
        this.dialogService.open(FeedbackComponent, {
            header: this.translateService.instant('artemisApp.result.detail.feedbackForTask', { taskName }),
            width: '50rem',
            modal: true,
            closable: true,
            closeOnEscape: true,
            dismissableMask: true,
            data: {
                exercise: this.exercise,
                result: this.latestResult,
                participation: this.participation,
                feedbackFilter: tests,
                exerciseType: ExerciseType.PROGRAMMING,
                taskName,
                numberOfNotExecutedTests: notExecutedTests.length,
            },
        });
    }
}
