import { Component, effect, inject, input, signal } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { MathExercise } from 'app/math/shared/entities/math-exercise.model';
import { MathExerciseService } from '../service/math-exercise.service';
import { RouterLink } from '@angular/router';
import { ExerciseComponent } from 'app/exercise/exercise.component';
import { onError } from 'app/shared/util/global.utils';
import { AccountService } from 'app/core/auth/account.service';
import { SortService } from 'app/shared/service/sort.service';
import { AlertService } from 'app/shared/service/alert.service';
import { faBook, faListAlt, faPlus, faSort, faTable, faTrash, faWrench } from '@fortawesome/free-solid-svg-icons';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { FormsModule } from '@angular/forms';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { ExerciseCategoriesComponent } from 'app/exercise/exercise-categories/exercise-categories.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { ButtonModule } from 'primeng/button';

@Component({
    selector: 'jhi-math-exercise',
    templateUrl: './math-exercise.component.html',
    imports: [
        SortDirective,
        FormsModule,
        SortByDirective,
        TranslateDirective,
        FaIconComponent,
        RouterLink,
        ExerciseCategoriesComponent,
        ArtemisDatePipe,
        DeleteButtonDirective,
        ButtonModule,
    ],
})
export class MathExerciseComponent extends ExerciseComponent {
    protected mathExerciseService = inject(MathExerciseService);
    private courseExerciseService = inject(CourseExerciseService);
    private alertService = inject(AlertService);
    private sortService = inject(SortService);
    private accountService = inject(AccountService);

    mathExercises = input<MathExercise[]>([]);
    internalMathExercises = signal<MathExercise[]>([]);
    filteredMathExercises: MathExercise[] = [];

    // Icons
    faSort = faSort;
    faPlus = faPlus;
    faTrash = faTrash;
    faTable = faTable;
    faListAlt = faListAlt;
    faBook = faBook;
    faWrench = faWrench;

    constructor() {
        super();
        effect(() => {
            const inputValue = this.mathExercises();
            this.internalMathExercises.set(inputValue ?? []);
        });
    }

    protected get exercises() {
        return this.internalMathExercises();
    }

    protected loadExercises(): void {
        this.courseExerciseService.findAllMathExercisesForCourse(this.courseId).subscribe({
            next: (res: HttpResponse<MathExercise[]>) => {
                const exercises = res.body ?? [];
                exercises.forEach((exercise) => {
                    exercise.course = this.course;
                    this.accountService.setAccessRightsForExercise(exercise);
                });
                this.internalMathExercises.set(exercises);
                this.applyFilter();
                this.emitExerciseCount(this.internalMathExercises().length);
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    protected applyFilter(): void {
        this.filteredMathExercises = this.internalMathExercises().filter((exercise) => this.filter.matchesExercise(exercise));
        this.emitFilteredExerciseCount(this.filteredMathExercises.length);
    }

    deleteExercise(exerciseId: number) {
        this.mathExerciseService.delete(exerciseId).subscribe({
            next: () => {
                this.eventManager.broadcast({
                    name: 'mathExerciseListModification',
                    content: 'Deleted an exercise',
                });
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    trackId(_index: number, item: MathExercise) {
        return item.id;
    }

    protected getChangeEventName(): string {
        return 'mathExerciseListModification';
    }

    sortRows() {
        const exercises = [...this.internalMathExercises()];
        this.sortService.sortByProperty(exercises, this.predicate, this.reverse);
        this.internalMathExercises.set(exercises);
        this.applyFilter();
    }

    callback() {}
}
