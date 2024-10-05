import { Component, Input, inject } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { QuizExercise, QuizMode, QuizStatus } from 'app/entities/quiz/quiz-exercise.model';
import { QuizExerciseService } from './quiz-exercise.service';
import { AccountService } from 'app/core/auth/account.service';
import { ExerciseComponent } from 'app/exercises/shared/exercise/exercise.component';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { SortService } from 'app/shared/service/sort.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { AlertService } from 'app/core/util/alert.service';
import { faSort, faTrash } from '@fortawesome/free-solid-svg-icons';
import { isQuizEditable } from 'app/exercises/quiz/shared/quiz-manage-util.service';

@Component({
    selector: 'jhi-quiz-exercise',
    templateUrl: './quiz-exercise.component.html',
})
export class QuizExerciseComponent extends ExerciseComponent {
    quizExerciseService = inject(QuizExerciseService);
    private accountService = inject(AccountService);
    private alertService = inject(AlertService);
    private sortService = inject(SortService);
    exerciseService = inject(ExerciseService);

    readonly ActionType = ActionType;
    readonly QuizStatus = QuizStatus;
    readonly QuizMode = QuizMode;

    @Input() quizExercises: QuizExercise[] = [];
    filteredQuizExercises: QuizExercise[] = [];

    // Icons
    faSort = faSort;
    faTrash = faTrash;

    protected get exercises() {
        return this.quizExercises;
    }

    protected loadExercises(): void {
        this.quizExerciseService.findForCourse(this.courseId).subscribe({
            next: (res: HttpResponse<QuizExercise[]>) => {
                this.quizExercises = res.body!;
                // reconnect exercise with course
                this.quizExercises.forEach((exercise) => {
                    exercise.course = this.course;
                    exercise.isAtLeastTutor = this.accountService.isAtLeastTutorInCourse(exercise.course);
                    exercise.isAtLeastEditor = this.accountService.isAtLeastEditorInCourse(exercise.course);
                    exercise.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(exercise.course);
                    exercise.quizBatches = exercise.quizBatches?.sort((a, b) => (a.id ?? 0) - (b.id ?? 0));
                    exercise.isEditable = isQuizEditable(exercise);
                    this.selectedExercises = [];
                });
                this.setQuizExercisesStatus();
                this.emitExerciseCount(this.quizExercises.length);
                this.applyFilter();
            },
            error: (res: HttpErrorResponse) => this.onError(res),
        });
    }

    protected applyFilter(): void {
        this.filteredQuizExercises = this.quizExercises.filter((exercise) => this.filter.matchesExercise(exercise));
        this.emitFilteredExerciseCount(this.filteredQuizExercises.length);
    }

    /**
     * Get the id of the quiz exercise
     * @param index the index of the quiz (not used at the moment)
     * @param item the quiz exercise of which the id should be returned
     */
    trackId(index: number, item: QuizExercise) {
        return item.id!;
    }

    protected getChangeEventName(): string {
        return 'quizExerciseListModification';
    }

    private onError(error: HttpErrorResponse) {
        this.alertService.error(error.headers.get('X-artemisApp-error')!);
    }

    /**
     * Convert seconds to full minutes
     * @param seconds {number} the number of seconds
     * @returns {number} the number of full minutes
     */
    fullMinutesForSeconds(seconds: number) {
        return Math.floor(seconds / 60);
    }

    /**
     * Set the quiz exercise status for all quiz exercises.
     */
    setQuizExercisesStatus() {
        this.quizExercises.forEach((quizExercise) => (quizExercise.status = this.quizExerciseService.getStatus(quizExercise)));
    }

    /**
     * Do not load all quizExercise if only one has changed
     *
     * @param quizExerciseId
     */
    loadOne(quizExerciseId: number) {
        this.quizExerciseService.find(quizExerciseId).subscribe((res: HttpResponse<QuizExercise>) => {
            this.handleNewQuizExercise(res.body!);
        });
    }

    handleNewQuizExercise(newQuizExercise: QuizExercise) {
        const index = this.quizExercises.findIndex((quizExercise) => quizExercise.id === newQuizExercise.id);
        newQuizExercise.isAtLeastTutor = this.accountService.isAtLeastTutorInCourse(newQuizExercise.course);
        newQuizExercise.isAtLeastEditor = this.accountService.isAtLeastEditorInCourse(newQuizExercise.course);
        newQuizExercise.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(newQuizExercise.course);
        newQuizExercise.status = this.quizExerciseService.getStatus(newQuizExercise);
        newQuizExercise.quizBatches = newQuizExercise.quizBatches?.sort((a, b) => (a.id ?? 0) - (b.id ?? 0));
        newQuizExercise.isEditable = isQuizEditable(newQuizExercise);
        if (index === -1) {
            this.quizExercises.push(newQuizExercise);
        } else {
            this.quizExercises[index] = newQuizExercise;
        }
        this.applyFilter();
    }

    public sortRows() {
        this.sortService.sortByProperty(this.quizExercises, this.predicate, this.reverse);
        this.applyFilter();
    }
}
