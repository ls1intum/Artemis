import { Component, inject, model } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { QuizExercise, QuizMode, QuizStatus } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizExerciseService } from '../service/quiz-exercise.service';
import { AccountService } from 'app/core/auth/account.service';
import { ExerciseComponent } from 'app/exercise/exercise.component';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { SortService } from 'app/shared/service/sort.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { AlertService } from 'app/shared/service/alert.service';
import { faSort, faTrash } from '@fortawesome/free-solid-svg-icons';
import { isQuizEditable } from 'app/quiz/shared/service/quiz-manage-util.service';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { FormsModule } from '@angular/forms';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { RouterLink } from '@angular/router';
import { QuizExerciseLifecycleButtonsComponent } from '../lifecyle-buttons/quiz-exercise-lifecycle-buttons.component';
import { QuizExerciseManageButtonsComponent } from '../manage-buttons/quiz-exercise-manage-buttons.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ExerciseCategoriesComponent } from 'app/exercise/exercise-categories/exercise-categories.component';

@Component({
    selector: 'jhi-quiz-exercise',
    templateUrl: './quiz-exercise.component.html',
    imports: [
        SortDirective,
        FormsModule,
        SortByDirective,
        TranslateDirective,
        FaIconComponent,
        RouterLink,
        QuizExerciseLifecycleButtonsComponent,
        ExerciseCategoriesComponent,
        QuizExerciseManageButtonsComponent,
        DeleteButtonDirective,
        ArtemisDatePipe,
    ],
})
export class QuizExerciseComponent extends ExerciseComponent {
    protected exerciseService = inject(ExerciseService); // needed in html code
    protected quizExerciseService = inject(QuizExerciseService); // needed in html code
    private accountService = inject(AccountService);
    private alertService = inject(AlertService);
    private sortService = inject(SortService);

    readonly ActionType = ActionType;
    readonly QuizStatus = QuizStatus;
    readonly QuizMode = QuizMode;

    readonly quizExercises = model<QuizExercise[]>([]);
    filteredQuizExercises: QuizExercise[] = [];

    // Icons
    faSort = faSort;
    faTrash = faTrash;

    protected get exercises() {
        return this.quizExercises();
    }

    protected loadExercises(): void {
        this.quizExerciseService.findForCourse(this.courseId).subscribe({
            next: (res: HttpResponse<QuizExercise[]>) => {
                const quizExercises = res.body!;
                // reconnect exercise with course
                quizExercises.forEach((exercise) => {
                    exercise.course = this.course;
                    exercise.isAtLeastTutor = this.accountService.isAtLeastTutorInCourse(exercise.course);
                    exercise.isAtLeastEditor = this.accountService.isAtLeastEditorInCourse(exercise.course);
                    exercise.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(exercise.course);
                    exercise.quizBatches = exercise.quizBatches?.sort((a, b) => (a.id ?? 0) - (b.id ?? 0));
                    exercise.status = this.quizExerciseService.getStatus(exercise);
                    exercise.isEditable = (exercise.isEditable ?? true) && isQuizEditable(exercise);
                    this.selectedExercises = [];
                });
                this.quizExercises.set(quizExercises);
                this.setQuizExercisesStatus();
                this.emitExerciseCount(quizExercises.length);
                this.applyFilter();
            },
            error: (res: HttpErrorResponse) => this.onError(res),
        });
    }

    protected applyFilter(): void {
        this.filteredQuizExercises = this.quizExercises().filter((exercise) => this.filter.matchesExercise(exercise));
        this.emitFilteredExerciseCount(this.filteredQuizExercises.length);
    }

    /**
     * Get the id of the quiz exercise
     * @param _index the index of the quiz (not used at the moment)
     * @param item the quiz exercise of which the id should be returned
     */
    trackId(_index: number, item: QuizExercise) {
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
     * @param seconds the number of seconds
     * @returns the number of full minutes
     */
    fullMinutesForSeconds(seconds: number) {
        return Math.floor(seconds / 60);
    }

    /**
     * Set the quiz exercise status for all quiz exercises.
     */
    setQuizExercisesStatus() {
        this.quizExercises().forEach((quizExercise) => (quizExercise.status = this.quizExerciseService.getStatus(quizExercise)));
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
        const index = this.quizExercises().findIndex((quizExercise) => quizExercise.id === newQuizExercise.id);
        newQuizExercise.isAtLeastTutor = this.accountService.isAtLeastTutorInCourse(newQuizExercise.course);
        newQuizExercise.isAtLeastEditor = this.accountService.isAtLeastEditorInCourse(newQuizExercise.course);
        newQuizExercise.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(newQuizExercise.course);
        newQuizExercise.status = this.quizExerciseService.getStatus(newQuizExercise);
        newQuizExercise.quizBatches = newQuizExercise.quizBatches?.sort((a, b) => (a.id ?? 0) - (b.id ?? 0));
        newQuizExercise.isEditable = isQuizEditable(newQuizExercise);
        const quizExercises = this.quizExercises().slice();
        if (index === -1) {
            quizExercises.push(newQuizExercise);
        } else {
            quizExercises[index] = newQuizExercise;
        }
        this.quizExercises.set(quizExercises);
        this.applyFilter();
    }

    public sortRows() {
        this.sortService.sortByProperty(this.quizExercises(), this.predicate, this.reverse);
        this.applyFilter();
    }
}
