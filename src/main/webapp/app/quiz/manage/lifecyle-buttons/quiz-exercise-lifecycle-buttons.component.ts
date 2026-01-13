import { Component, inject } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { QuizBatch, QuizExercise, QuizMode, QuizStatus } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizExerciseService } from '../service/quiz-exercise.service';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { AlertService } from 'app/shared/service/alert.service';
import { faEye, faFileExport, faPlayCircle, faPlus, faSignal, faSort, faStopCircle, faTable, faTimes, faWrench } from '@fortawesome/free-solid-svg-icons';
import { Subject } from 'rxjs';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { input, output } from '@angular/core';

@Component({
    selector: 'jhi-quiz-exercise-lifecycle-buttons',
    templateUrl: './quiz-exercise-lifecycle-buttons.component.html',
    imports: [FaIconComponent, TranslateDirective, DeleteButtonDirective],
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

    readonly quizExercise = input.required<QuizExercise>();

    readonly loadOne = output<number>();
    readonly handleNewQuizExercise = output<QuizExercise>();

    /**
     * Set the quiz open for practice
     */
    openForPractice() {
        this.quizExerciseService.openForPractice(this.quizExercise().id!).subscribe({
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
        this.quizExerciseService.start(this.quizExercise().id!).subscribe({
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
        return this.quizExerciseService.end(this.quizExercise().id!).subscribe({
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
                this.loadOne.emit(this.quizExercise().id!);
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
                if (!this.quizExercise().quizBatches) {
                    this.quizExercise().quizBatches = [];
                }
                this.quizExercise().quizBatches?.push(res.body!);
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
        this.loadOne.emit(this.quizExercise().id!);
    }
}
