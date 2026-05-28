import { Component, effect, inject, input, signal } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ProofExercise } from 'app/proof/shared/entities/proof-exercise.model';
import { ProofExerciseService } from '../service/proof-exercise.service';
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
    selector: 'jhi-proof-exercise',
    templateUrl: './proof-exercise.component.html',
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
export class ProofExerciseComponent extends ExerciseComponent {
    protected proofExerciseService = inject(ProofExerciseService);
    private courseExerciseService = inject(CourseExerciseService);
    private alertService = inject(AlertService);
    private sortService = inject(SortService);
    private accountService = inject(AccountService);

    proofExercises = input<ProofExercise[]>([]);
    internalProofExercises = signal<ProofExercise[]>([]);
    filteredProofExercises: ProofExercise[] = [];

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
            const inputValue = this.proofExercises();
            this.internalProofExercises.set(inputValue ?? []);
        });
    }

    protected get exercises() {
        return this.internalProofExercises();
    }

    protected loadExercises(): void {
        this.courseExerciseService.findAllProofExercisesForCourse(this.courseId).subscribe({
            next: (res: HttpResponse<ProofExercise[]>) => {
                const exercises = res.body ?? [];
                exercises.forEach((exercise) => {
                    exercise.course = this.course;
                    this.accountService.setAccessRightsForExercise(exercise);
                });
                this.internalProofExercises.set(exercises);
                this.applyFilter();
                this.emitExerciseCount(this.internalProofExercises().length);
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    protected applyFilter(): void {
        this.filteredProofExercises = this.internalProofExercises().filter((exercise) => this.filter.matchesExercise(exercise));
        this.emitFilteredExerciseCount(this.filteredProofExercises.length);
    }

    deleteExercise(exerciseId: number) {
        this.proofExerciseService.delete(exerciseId).subscribe({
            next: () => {
                this.eventManager.broadcast({
                    name: 'proofExerciseListModification',
                    content: 'Deleted an exercise',
                });
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    trackId(_index: number, item: ProofExercise) {
        return item.id;
    }

    protected getChangeEventName(): string {
        return 'proofExerciseListModification';
    }

    sortRows() {
        const exercises = [...this.internalProofExercises()];
        this.sortService.sortByProperty(exercises, this.predicate, this.reverse);
        this.internalProofExercises.set(exercises);
        this.applyFilter();
    }

    callback() {}
}
