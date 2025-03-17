import { Component, Input, inject } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { filter } from 'rxjs/operators';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { FileUploadExerciseService } from './file-upload-exercise.service';
import { ExerciseComponent } from 'app/exercises/shared/exercise/exercise.component';
import { onError } from 'app/shared/util/global.utils';
import { AccountService } from 'app/core/auth/account.service';
import { SortService } from 'app/shared/service/sort.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { AlertService } from 'app/shared/service/alert.service';
import { faBook, faPlus, faSort, faTable, faTrash, faUsers, faWrench } from '@fortawesome/free-solid-svg-icons';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { FormsModule } from '@angular/forms';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { RouterLink } from '@angular/router';
import { ExerciseCategoriesComponent } from 'app/shared/exercise-categories/exercise-categories.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';

@Component({
    selector: 'jhi-file-upload-exercise',
    templateUrl: './file-upload-exercise.component.html',
    imports: [SortDirective, FormsModule, SortByDirective, TranslateDirective, FaIconComponent, RouterLink, ExerciseCategoriesComponent, DeleteButtonDirective, ArtemisDatePipe],
})
export class FileUploadExerciseComponent extends ExerciseComponent {
    protected exerciseService = inject(ExerciseService); // needed in html code
    protected fileUploadExerciseService = inject(FileUploadExerciseService); // needed in html code
    private courseExerciseService = inject(CourseExerciseService);
    private alertService = inject(AlertService);
    private accountService = inject(AccountService);
    private sortService = inject(SortService);

    @Input() fileUploadExercises: FileUploadExercise[] = [];
    filteredFileUploadExercises: FileUploadExercise[] = [];

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
        return this.fileUploadExercises;
    }

    protected loadExercises(): void {
        this.courseExerciseService
            .findAllFileUploadExercisesForCourse(this.courseId)
            .pipe(filter((res) => !!res.body))
            .subscribe({
                next: (res: HttpResponse<FileUploadExercise[]>) => {
                    this.fileUploadExercises = res.body!;
                    // reconnect exercise with course
                    this.fileUploadExercises.forEach((exercise) => {
                        exercise.course = this.course;
                        this.accountService.setAccessRightsForExercise(exercise);
                        this.selectedExercises = [];
                    });
                    this.emitExerciseCount(this.fileUploadExercises.length);
                    this.applyFilter();
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    protected applyFilter(): void {
        this.filteredFileUploadExercises = this.fileUploadExercises.filter((exercise) => this.filter.matchesExercise(exercise));
        this.emitFilteredExerciseCount(this.filteredFileUploadExercises.length);
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
    deleteFileUploadExercise(fileUploadExerciseId: number) {
        this.fileUploadExerciseService.delete(fileUploadExerciseId).subscribe({
            next: () => {
                this.eventManager.broadcast({
                    name: 'fileUploadExerciseListModification',
                    content: 'Deleted an fileUploadExercise',
                });
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    protected getChangeEventName(): string {
        return 'fileUploadExerciseListModification';
    }

    sortRows() {
        this.sortService.sortByProperty(this.fileUploadExercises, this.predicate, this.reverse);
        this.applyFilter();
    }

    /**
     * Used in the template for jhiSort
     */
    callback() {}
}
