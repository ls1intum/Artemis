import { Component, computed, inject, input } from '@angular/core';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { DialogService } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { ProgrammingExerciseInstructionService, TestCaseState } from 'app/programming/shared/instructions-render/services/programming-exercise-instruction.service';
import { TaskArray } from 'app/programming/shared/instructions-render/task/programming-exercise-task.model';
import { FeedbackComponent } from 'app/exercise/feedback/feedback.component';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { faCheck, faCircle, faTimes } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
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
    private dialogService = inject(DialogService);
    private translateService = inject(TranslateService);
    private instructionService = inject(ProgrammingExerciseInstructionService);

    TestCaseState = TestCaseState;

    readonly exercise = input<Exercise>();
    readonly participation = input<Participation>();
    readonly latestResult = input<Result>();
    readonly tasks = input.required<TaskArray>();

    readonly steps = computed<StepWizardStep[]>(() => {
        // Parent template binds a class field that can be undefined on first render despite `input.required`.
        const tasks = this.tasks();
        const latestResult = this.latestResult();
        if (!tasks) {
            return [];
        }
        return tasks.map(({ taskName, testIds }) => ({
            done: this.instructionService.testStatusForTask(testIds, latestResult).testCaseState,
            title: taskName,
            testIds,
        }));
    });

    faTimes = faTimes;
    faCheck = faCheck;
    faCircle = faCircle;

    showDetailsForTests(tests: number[], taskName: string) {
        const latestResult = this.latestResult();
        if (!latestResult || !tests.length) {
            return;
        }
        const {
            detailed: { notExecutedTests },
        } = this.instructionService.testStatusForTask(tests, latestResult);
        this.dialogService.open(FeedbackComponent, {
            header: this.translateService.instant('artemisApp.result.detail.feedbackForTask', { taskName }),
            width: '50rem',
            breakpoints: {
                '850px': '95vw',
            },
            modal: true,
            closable: true,
            closeOnEscape: true,
            dismissableMask: true,
            data: {
                exercise: this.exercise(),
                result: latestResult,
                participation: this.participation(),
                feedbackFilter: tests,
                exerciseType: ExerciseType.PROGRAMMING,
                taskName,
                numberOfNotExecutedTests: notExecutedTests.length,
            },
        });
    }
}
