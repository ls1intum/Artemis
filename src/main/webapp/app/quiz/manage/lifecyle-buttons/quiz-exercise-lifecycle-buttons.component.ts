import { Component, computed, inject, input, output } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { QuizExercise, QuizMode, QuizStatus } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizExerciseLifecycleService, canEndQuiz, canManageQuizBatches, canSetQuizVisible, canStartQuiz } from 'app/quiz/manage/lifecyle-buttons/quiz-exercise-lifecycle.service';
import { ActionType } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { faBoxesStacked, faEye, faPlayCircle, faPlus, faStopCircle } from '@fortawesome/free-solid-svg-icons';
import { Subject } from 'rxjs';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TumAetUiButtonDirective, TumAetUiPopoverComponent, TumAetUiPopoverTriggerDirective, TumAetUiTagComponent, TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { cloneWith, deepClone } from 'app/foundation/util/deep-clone.util';
import { QuizExerciseBatchApi } from 'app/openapi/api/quiz-exercise-batch-api';
import { toQuizBatch } from 'app/quiz/shared/util/generated-quiz-exercise.util';

@Component({
    selector: 'jhi-quiz-exercise-lifecycle-buttons',
    templateUrl: './quiz-exercise-lifecycle-buttons.component.html',
    // flex-nowrap: keep the lifecycle buttons (e.g. Batches + End) on a single line rather than stacking them when space
    // is tight — they are a related action group, and the exercise-management table measures their inline width to size
    // the actions column (a wrap would mis-measure it).
    host: { class: 'flex flex-nowrap items-center gap-1 [&_button]:whitespace-nowrap' },
    imports: [
        FaIconComponent,
        TranslateDirective,
        DeleteButtonDirective,
        ArtemisTranslatePipe,
        TumAetUiButtonDirective,
        TumAetUiTagComponent,
        TumAetUiTooltipDirective,
        TumAetUiPopoverComponent,
        TumAetUiPopoverTriggerDirective,
    ],
})
export class QuizExerciseLifecycleButtonsComponent {
    private quizExerciseLifecycleService = inject(QuizExerciseLifecycleService);
    private quizExerciseBatchApi = inject(QuizExerciseBatchApi);
    private alertService = inject(AlertService);

    protected readonly QuizMode = QuizMode;
    protected readonly QuizStatus = QuizStatus;
    protected readonly ActionType = ActionType;

    // Icons
    faBoxesStacked = faBoxesStacked;
    faPlus = faPlus;
    faEye = faEye;
    faPlayCircle = faPlayCircle;
    faStopCircle = faStopCircle;

    protected dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    readonly quizExercise = input.required<QuizExercise>();
    /** Style of the lifecycle buttons: outlined next to the outlined row actions of the exercise table. */
    readonly variant = input<'solid' | 'outlined'>('solid');
    /** Shows only the batch menu, where the other lifecycle actions are offered elsewhere, such as the exercise table's row actions. */
    readonly batchesOnly = input(false);

    readonly loadOne = output<number>();
    readonly handleNewQuizExercise = output<QuizExercise>();

    protected readonly isInVariantGroup = computed(() => !!this.quizExercise().exerciseVariantGroup);

    readonly showBatchMenu = computed<boolean>(() => canManageQuizBatches(this.quizExercise()));
    protected readonly showStartButton = computed<boolean>(() => !this.batchesOnly() && canStartQuiz(this.quizExercise()));
    protected readonly showEndButton = computed<boolean>(() => !this.batchesOnly() && canEndQuiz(this.quizExercise()));
    protected readonly showSetVisibleButton = computed<boolean>(() => !this.batchesOnly() && canSetQuizVisible(this.quizExercise()));

    /**
     * Start the given quiz-exercise immediately
     */
    startQuiz() {
        this.quizExerciseLifecycleService.startQuiz(this.quizExercise()).subscribe({
            next: (updatedExercise) => this.handleNewQuizExercise.emit(updatedExercise),
            error: (res: HttpErrorResponse) => {
                this.onError(res);
            },
        });
    }

    /**
     * End the given quiz-exercise immediately
     */
    endQuiz() {
        return this.quizExerciseLifecycleService.endQuiz(this.quizExercise()).subscribe({
            next: (updatedExercise) => {
                this.handleNewQuizExercise.emit(updatedExercise);
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    /**
     * Start the given quiz-batch immediately
     *
     * @param quizBatchId the quiz batch id to start
     */
    startBatch(quizBatchId: number) {
        this.quizExerciseBatchApi.startBatch(quizBatchId).subscribe({
            next: () => {
                const updatedExercise = deepClone(this.quizExercise());
                if (updatedExercise.quizBatches) {
                    updatedExercise.quizBatches = updatedExercise.quizBatches.map((batch) => {
                        if (batch.id === quizBatchId) {
                            return cloneWith(batch, { started: true });
                        }
                        return batch;
                    });
                    this.handleNewQuizExercise.emit(updatedExercise);
                }
            },
            error: (res: HttpErrorResponse) => {
                this.onError(res);
            },
        });
    }

    /**
     * Adds a new batch to the given quiz
     */
    addBatch() {
        this.quizExerciseBatchApi.addBatch(this.quizExercise().id!).subscribe({
            next: (batch) => {
                const updatedExercise = deepClone(this.quizExercise());
                const newBatch = toQuizBatch(batch);

                const currentBatches = updatedExercise.quizBatches ? [...updatedExercise.quizBatches] : [];
                currentBatches.push(newBatch);
                updatedExercise.quizBatches = currentBatches;

                this.handleNewQuizExercise.emit(updatedExercise);
            },
            error: (res: HttpErrorResponse) => {
                this.onError(res);
            },
        });
    }

    /**
     * Make the given quiz-exercise visible to students
     */
    showQuiz() {
        this.quizExerciseLifecycleService.setVisible(this.quizExercise()).subscribe({
            next: (updatedExercise) => this.handleNewQuizExercise.emit(updatedExercise),
            error: (res: HttpErrorResponse) => {
                this.onError(res);
            },
        });
    }

    private onError(error: HttpErrorResponse) {
        this.alertService.error(error.headers.get('X-artemisApp-error')!);
        this.loadOne.emit(this.quizExercise().id!);
    }
}
