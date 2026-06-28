import { Component, computed, inject, input, output, viewChild } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { QuizBatch, QuizExercise, QuizMode, QuizStatus } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizExerciseService } from '../service/quiz-exercise.service';
import { ActionType } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { faBoxesStacked, faEye, faFileExport, faPlayCircle, faPlus, faSort, faStopCircle, faTable, faTimes, faWrench } from '@fortawesome/free-solid-svg-icons';
import { Subject } from 'rxjs';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TooltipModule } from 'primeng/tooltip';
import { Popover, PopoverModule } from 'primeng/popover';
import { QuizExerciseDates } from 'app/quiz/shared/entities/quiz-exercise-dates.model';

@Component({
    selector: 'jhi-quiz-exercise-lifecycle-buttons',
    templateUrl: './quiz-exercise-lifecycle-buttons.component.html',
    styleUrl: './quiz-exercise-lifecycle-buttons.component.scss',
    host: { class: 'd-flex gap-1 align-items-center flex-wrap' },
    imports: [FaIconComponent, TranslateDirective, DeleteButtonDirective, ArtemisTranslatePipe, TooltipModule, PopoverModule],
})
export class QuizExerciseLifecycleButtonsComponent {
    private quizExerciseService = inject(QuizExerciseService);
    private alertService = inject(AlertService);

    protected readonly QuizMode = QuizMode;
    protected readonly QuizStatus = QuizStatus;
    protected readonly ActionType = ActionType;

    // Icons
    faBoxesStacked = faBoxesStacked;
    faSort = faSort;
    faPlus = faPlus;
    faTimes = faTimes;
    faEye = faEye;
    faWrench = faWrench;
    faTable = faTable;
    faFileExport = faFileExport;
    faPlayCircle = faPlayCircle;
    faStopCircle = faStopCircle;

    protected dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    readonly quizExercise = input.required<QuizExercise>();

    readonly loadOne = output<number>();
    readonly handleNewQuizExercise = output<QuizExercise>();

    protected readonly isInVariantGroup = computed(() => !!this.quizExercise().exerciseVariantGroup);

    private readonly batchMenu = viewChild<Popover>('batchMenu');

    readonly showBatchMenu = computed<boolean>(() => {
        const quiz = this.quizExercise();
        return quiz.quizMode === QuizMode.BATCHED && (quiz.status === QuizStatus.VISIBLE || quiz.status === QuizStatus.ACTIVE);
    });

    protected readonly showStartButton = computed<boolean>(() => {
        const quiz = this.quizExercise();
        return (
            (quiz.status === QuizStatus.VISIBLE || quiz.status === QuizStatus.INVISIBLE) && quiz.quizMode === QuizMode.SYNCHRONIZED && !!quiz.isAtLeastEditor && !quiz.quizStarted
        );
    });

    protected readonly showEndButton = computed<boolean>(() => {
        const quiz = this.quizExercise();
        return (
            (quiz.status === QuizStatus.VISIBLE || quiz.status === QuizStatus.ACTIVE) && quiz.quizMode !== QuizMode.SYNCHRONIZED && !!quiz.isAtLeastInstructor && !quiz.quizEnded
        );
    });

    protected readonly showSetVisibleButton = computed<boolean>(() => {
        const quiz = this.quizExercise();
        return quiz.status === QuizStatus.INVISIBLE && !!quiz.isAtLeastEditor && !quiz.visibleToStudents;
    });

    protected toggleBatchMenu(event: MouseEvent): void {
        this.batchMenu()?.toggle(event);
    }

    /**
     * Start the given quiz-exercise immediately
     */
    startQuiz() {
        this.quizExerciseService.start(this.quizExercise().id!).subscribe({
            next: (res: HttpResponse<QuizExerciseDates>) => {
                const updatedExercise = { ...this.quizExercise() };

                this.applyDatesToExercise(updatedExercise, res.body!);
                updatedExercise.visibleToStudents = true;
                updatedExercise.status = QuizStatus.ACTIVE;
                const batches = updatedExercise.quizBatches ? [...updatedExercise.quizBatches] : [];
                if (batches.length > 0) {
                    const firstBatch = { ...batches[0] };
                    firstBatch.started = true;
                    firstBatch.startTime = updatedExercise.startDate;
                    batches[0] = firstBatch;
                } else {
                    batches.push({
                        started: true,
                        startTime: updatedExercise.startDate,
                    });
                }
                updatedExercise.quizBatches = batches;
                this.handleNewQuizExercise.emit(updatedExercise);
            },
            error: (res: HttpErrorResponse) => {
                this.onError(res);
            },
        });
    }

    /**
     * End the given quiz-exercise immediately
     */
    endQuiz() {
        return this.quizExerciseService.end(this.quizExercise().id!).subscribe({
            next: (res: HttpResponse<QuizExerciseDates>) => {
                const updatedExercise = { ...this.quizExercise() };
                this.applyDatesToExercise(updatedExercise, res.body!);
                updatedExercise.quizEnded = true;
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
        this.quizExerciseService.startBatch(quizBatchId).subscribe({
            next: () => {
                const updatedExercise = { ...this.quizExercise() };
                if (updatedExercise.quizBatches) {
                    updatedExercise.quizBatches = updatedExercise.quizBatches.map((batch) => {
                        if (batch.id === quizBatchId) {
                            return { ...batch, started: true };
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
        this.quizExerciseService.addBatch(this.quizExercise().id!).subscribe({
            next: (res: HttpResponse<QuizBatch>) => {
                const updatedExercise = { ...this.quizExercise() };
                const newBatch = res.body!;

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
        this.quizExerciseService.setVisible(this.quizExercise().id!).subscribe({
            next: (res: HttpResponse<QuizExerciseDates>) => {
                const updatedExercise = { ...this.quizExercise() };
                this.applyDatesToExercise(updatedExercise, res.body!);
                updatedExercise.visibleToStudents = true;
                this.handleNewQuizExercise.emit(updatedExercise);
            },
            error: (res: HttpErrorResponse) => {
                this.onError(res);
            },
        });
    }

    private onError(error: HttpErrorResponse) {
        this.alertService.error(error.headers.get('X-artemisApp-error')!);
        this.loadOne.emit(this.quizExercise().id!);
    }

    private applyDatesToExercise(exercise: QuizExercise, dates: QuizExerciseDates) {
        exercise.releaseDate = dates.releaseDate;
        exercise.startDate = dates.startDate;
        exercise.dueDate = dates.dueDate;
    }
}
