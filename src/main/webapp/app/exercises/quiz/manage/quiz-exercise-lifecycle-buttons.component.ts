import { Component, EventEmitter, Input, Output } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { QuizExercise, QuizMode, QuizStatus } from 'app/entities/quiz/quiz-exercise.model';
import { QuizExerciseService } from './quiz-exercise.service';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { AlertService } from 'app/core/util/alert.service';
import { faEye, faFileExport, faPlayCircle, faPlus, faSignal, faSort, faStopCircle, faTable, faTimes, faWrench } from '@fortawesome/free-solid-svg-icons';
import { Subject } from 'rxjs';

@Component({
    selector: 'jhi-quiz-exercise-lifecycle-buttons',
    templateUrl: './quiz-exercise-lifecycle-buttons.component.html',
})
export class QuizExerciseLifecycleButtonsComponent {
    protected readonly QuizMode = QuizMode;
    protected readonly QuizStatus = QuizStatus;
    protected readonly ActionType = ActionType;

    // Icons
    faSort = faSort;
    faPlus = faPlus;
    faTimes = faTimes;
    faEye = faEye;
    faWrench = faWrench;
    faTable = faTable;
    faSignal = faSignal;
    faFileExport = faFileExport;
    faPlayCircle = faPlayCircle;
    faStopCircle = faStopCircle;

    protected dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    @Input()
    quizExercise: QuizExercise;

    @Output()
    loadOne = new EventEmitter<number>();

    @Output()
    handleNewQuizExercise = new EventEmitter<QuizExercise>();

    constructor(
        private quizExerciseService: QuizExerciseService,
        private alertService: AlertService,
    ) {}

    /**
     * Set the quiz open for practice
     *
     * @param quizExerciseId the quiz exercise id to start
     */
    openForPractice(quizExerciseId: number) {
        this.quizExerciseService.openForPractice(quizExerciseId).subscribe({
            next: (res: HttpResponse<QuizExercise>) => {
                this.handleNewQuizExercise.emit(res.body!);
            },
            error: (res: HttpErrorResponse) => {
                this.onError(res);
                this.loadOne.emit(quizExerciseId);
            },
        });
    }

    /**
     * Start the given quiz-exercise immediately
     *
     * @param quizExerciseId the quiz exercise id to start
     */
    startQuiz(quizExerciseId: number) {
        this.quizExerciseService.start(quizExerciseId).subscribe({
            next: (res: HttpResponse<QuizExercise>) => {
                this.handleNewQuizExercise.emit(res.body!);
            },
            error: (res: HttpErrorResponse) => {
                this.onError(res);
                this.loadOne.emit(quizExerciseId);
            },
        });
    }

    /**
     * End the given quiz-exercise immediately
     *
     * @param quizExerciseId the quiz exercise id to end
     */
    endQuiz(quizExerciseId: number) {
        return this.quizExerciseService.end(quizExerciseId).subscribe({
            next: (res: HttpResponse<QuizExercise>) => {
                this.handleNewQuizExercise.emit(res.body!);
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    /**
     * Start the given quiz-batch immediately
     *
     * @param quizExerciseId the quiz exercise id the batch belongs to
     * @param quizBatchId the quiz batch id to start
     */
    startBatch(quizExerciseId: number, quizBatchId: number) {
        this.quizExerciseService.startBatch(quizBatchId).subscribe({
            next: () => {
                this.loadOne.emit(quizExerciseId);
            },
            error: (res: HttpErrorResponse) => {
                this.onError(res);
                this.loadOne.emit(quizExerciseId);
            },
        });
    }

    /**
     * Adds a new batch to the given quiz
     *
     * @param quizExerciseId the quiz exercise id to add a batch to
     */
    addBatch(quizExerciseId: number) {
        this.quizExerciseService.addBatch(quizExerciseId).subscribe({
            next: () => {
                this.loadOne.emit(quizExerciseId);
            },
            error: (res: HttpErrorResponse) => {
                this.onError(res);
                this.loadOne.emit(quizExerciseId);
            },
        });
    }

    /**
     * Make the given quiz-exercise visible to students
     *
     * @param quizExerciseId the quiz exercise id to start
     */
    showQuiz(quizExerciseId: number) {
        this.quizExerciseService.setVisible(quizExerciseId).subscribe({
            next: (res: HttpResponse<QuizExercise>) => {
                this.handleNewQuizExercise.emit(res.body!);
            },
            error: (res: HttpErrorResponse) => {
                this.onError(res);
                this.loadOne.emit(quizExerciseId);
            },
        });
    }

    private onError(error: HttpErrorResponse) {
        this.alertService.error(error.headers.get('X-artemisApp-error')!);
    }
}
