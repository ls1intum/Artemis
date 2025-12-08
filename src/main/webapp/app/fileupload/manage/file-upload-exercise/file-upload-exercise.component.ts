import { ChangeDetectionStrategy, Component, inject, model, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { filter } from 'rxjs/operators';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { FileUploadExerciseService } from '../services/file-upload-exercise.service';
import { ExerciseComponent } from 'app/exercise/exercise.component';
import { onError } from 'app/shared/util/global.utils';
import { AccountService } from 'app/core/auth/account.service';
import { SortService } from 'app/shared/service/sort.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { AlertService } from 'app/shared/service/alert.service';
import { faBook, faPlus, faSort, faTable, faTrash, faUsers, faWrench } from '@fortawesome/free-solid-svg-icons';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { FormsModule } from '@angular/forms';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { RouterLink } from '@angular/router';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { ExerciseCategoriesComponent } from 'app/exercise/exercise-categories/exercise-categories.component';

@Component({
    selector: 'jhi-file-upload-exercise',
    templateUrl: './file-upload-exercise.component.html',
    imports: [SortDirective, FormsModule, SortByDirective, TranslateDirective, FaIconComponent, RouterLink, ExerciseCategoriesComponent, DeleteButtonDirective, ArtemisDatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FileUploadExerciseComponent extends ExerciseComponent {
    protected exerciseService = inject(ExerciseService); // needed in html code
    protected fileUploadExerciseService = inject(FileUploadExerciseService); // needed in html code
    private courseExerciseService = inject(CourseExerciseService);
    private alertService = inject(AlertService);
    private accountService = inject(AccountService);
    private sortService = inject(SortService);

    fileUploadExercises = model<FileUploadExercise[]>([]);
    filteredFileUploadExercises = signal<FileUploadExercise[]>([]);

    // Icons
    faSort = faSort;
    faPlus = faPlus;
    faTrash = faTrash;
    faBook = faBook;
    faWrench = faWrench;
    faUsers = faUsers;
    faTable = faTable;
    farListAlt = faListAlt;

    protected get exercises() {
        return this.fileUploadExercises();
    }

    protected async loadExercises() {
        try {
            const res = await firstValueFrom(this.courseExerciseService.findAllFileUploadExercisesForCourse(this.courseId).pipe(filter((res) => !!res.body)));
            const exercises = res.body ?? [];
            this.fileUploadExercises.set(exercises);

            // reconnect exercise with course
            exercises.forEach((exercise) => {
                exercise.course = this.course;
                this.accountService.setAccessRightsForExercise(exercise);
            });
            this.selectedExercises = [];
            this.emitExerciseCount(exercises.length);
            this.applyFilter();
        } catch (error: unknown) {
            onError(this.alertService, error as HttpErrorResponse);
        }
    }

    protected applyFilter(): void {
        this.filteredFileUploadExercises.set(this.fileUploadExercises().filter((exercise) => this.filter.matchesExercise(exercise)));
        this.emitFilteredExerciseCount(this.filteredFileUploadExercises().length);
    }

    /**
     * Returns the unique identifier for items in the collection
     * @param _index of a file upload exercise in the collection
     * @param item current file upload exercise
     */
    trackId(_index: number, item: FileUploadExercise) {
        return item.id;
    }

    /**
     * Deletes file upload exercise
     * @param fileUploadExerciseId id of the exercise that will be deleted
     */
    async deleteFileUploadExercise(fileUploadExerciseId: number) {
        try {
            await firstValueFrom(this.fileUploadExerciseService.delete(fileUploadExerciseId));
            this.eventManager.broadcast({
                name: 'fileUploadExerciseListModification',
                content: 'Deleted an fileUploadExercise',
            });
            this.dialogErrorSource.next('');
        } catch (error: any) {
            this.dialogErrorSource.next(error.message);
        }
    }

    protected getChangeEventName(): string {
        return 'fileUploadExerciseListModification';
    }

    sortRows() {
        const sorted = this.sortService.sortByProperty([...this.fileUploadExercises()], this.predicate, this.reverse);
        this.fileUploadExercises.set(sorted);
        this.applyFilter();
    }

    /**
     * Used in the template for jhiSort
     */
    callback() {}
}
