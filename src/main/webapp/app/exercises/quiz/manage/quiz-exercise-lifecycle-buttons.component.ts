import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
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
    private quizExerciseService = inject(QuizExerciseService);
    private alertService = inject(AlertService);

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

    /**
     * Set the quiz open for practice
     */
    openForPractice() {
        this.quizExerciseService.openForPractice(this.quizExercise.id!).subscribe({
            next: (res: HttpResponse<QuizExercise>) => {
                this.handleNewQuizExercise.emit(res.body!);
            },
            error: (res: HttpErrorResponse) => {
                this.onError(res);
            },
        });
    }

    /**
     * Start the given quiz-exercise immediately
     */
    startQuiz() {
        this.quizExerciseService.start(this.quizExercise.id!).subscribe({
            next: (res: HttpResponse<QuizExercise>) => {
                this.handleNewQuizExercise.emit(res.body!);
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
        return this.quizExerciseService.end(this.quizExercise.id!).subscribe({
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
     * @param quizBatchId the quiz batch id to start
     */
    startBatch(quizBatchId: number) {
        this.quizExerciseService.startBatch(quizBatchId).subscribe({
            next: () => {
                this.loadOne.emit(this.quizExercise.id!);
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
        this.quizExerciseService.addBatch(this.quizExercise.id!).subscribe({
            next: () => {
                this.loadOne.emit(this.quizExercise.id!);
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
        this.quizExerciseService.setVisible(this.quizExercise.id!).subscribe({
            next: (res: HttpResponse<QuizExercise>) => {
                this.handleNewQuizExercise.emit(res.body!);
            },
            error: (res: HttpErrorResponse) => {
                this.onError(res);
            },
        });
    }

    private onError(error: HttpErrorResponse) {
        this.alertService.error(error.headers.get('X-artemisApp-error')!);
        this.loadOne.emit(this.quizExercise.id!);
    }
}
