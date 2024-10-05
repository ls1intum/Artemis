import { Component, Input, inject } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ModelingExerciseService } from './modeling-exercise.service';
import { AccountService } from 'app/core/auth/account.service';
import { ExerciseComponent } from 'app/exercises/shared/exercise/exercise.component';
import { onError } from 'app/shared/util/global.utils';
import { SortService } from 'app/shared/service/sort.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { AlertService } from 'app/core/util/alert.service';
import { faBook, faPlus, faSort, faTable, faTimes, faTrash, faUsers, faWrench } from '@fortawesome/free-solid-svg-icons';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';

@Component({
    selector: 'jhi-modeling-exercise',
    templateUrl: './modeling-exercise.component.html',
})
export class ModelingExerciseComponent extends ExerciseComponent {
    exerciseService = inject(ExerciseService);
    modelingExerciseService = inject(ModelingExerciseService);
    private courseExerciseService = inject(CourseExerciseService);
    private alertService = inject(AlertService);
    private accountService = inject(AccountService);
    private sortService = inject(SortService);

    @Input() modelingExercises: ModelingExercise[] = [];
    filteredModelingExercises: ModelingExercise[];
    // Icons
    faPlus = faPlus;
    faSort = faSort;
    faTable = faTable;
    farListAlt = faListAlt;
    faBook = faBook;
    faUsers = faUsers;
    faWrench = faWrench;
    faTimes = faTimes;
    faTrash = faTrash;

    protected get exercises() {
        return this.modelingExercises;
    }

    protected loadExercises(): void {
        this.courseExerciseService.findAllModelingExercisesForCourse(this.courseId).subscribe({
            next: (res: HttpResponse<ModelingExercise[]>) => {
                this.modelingExercises = res.body!;
                // reconnect exercise with course
                this.modelingExercises.forEach((exercise) => {
                    exercise.course = this.course;
                    this.accountService.setAccessRightsForExercise(exercise);
                    this.selectedExercises = [];
                });
                this.applyFilter();
                this.emitExerciseCount(this.modelingExercises.length);
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    protected applyFilter(): void {
        this.filteredModelingExercises = this.modelingExercises.filter((exercise) => this.filter.matchesExercise(exercise));
        this.emitFilteredExerciseCount(this.filteredModelingExercises.length);
    }

    /**
     * Returns the unique identifier for items in the collection
     * @param index of a modeling exercise in the collection
     * @param item current modeling exercise
     */
    trackId(index: number, item: ModelingExercise) {
        return item.id;
    }

    /**
     * Deletes modeling exercise
     * @param modelingExerciseId id of the exercise that will be deleted
     */
    deleteModelingExercise(modelingExerciseId: number) {
        this.modelingExerciseService.delete(modelingExerciseId).subscribe({
            next: () => {
                this.eventManager.broadcast({
                    name: 'modelingExerciseListModification',
                    content: 'Deleted an modelingExercise',
                });
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    protected getChangeEventName(): string {
        return 'modelingExerciseListModification';
    }

    sortRows() {
        this.sortService.sortByProperty(this.modelingExercises, this.predicate, this.reverse);
        this.applyFilter();
    }

    /**
     * Used in the template for jhiSort
     */
    callback() {}
}
