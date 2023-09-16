import { Component, Input } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { merge } from 'rxjs';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { filter } from 'rxjs/operators';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { FileUploadExerciseService } from './file-upload-exercise.service';
import { ExerciseComponent } from 'app/exercises/shared/exercise/exercise.component';
import { onError } from 'app/shared/util/global.utils';
import { AccountService } from 'app/core/auth/account.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { SortService } from 'app/shared/service/sort.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { AlertService } from 'app/core/util/alert.service';
import { EventManager } from 'app/core/util/event-manager.service';
import { faBook, faPlus, faSort, faTable, faTimes, faUsers, faWrench } from '@fortawesome/free-solid-svg-icons';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-file-upload-exercise',
    templateUrl: './file-upload-exercise.component.html',
})
export class FileUploadExerciseComponent extends ExerciseComponent {
    selectedFileUploadExercises: FileUploadExercise[];
    allChecked = false;

    @Input() fileUploadExercises: FileUploadExercise[] = [];
    filteredFileUploadExercises: FileUploadExercise[] = [];

    // Icons
    faSort = faSort;
    faPlus = faPlus;
    faTimes = faTimes;
    faBook = faBook;
    faWrench = faWrench;
    faUsers = faUsers;
    faTable = faTable;
    farListAlt = faListAlt;

    constructor(
        public exerciseService: ExerciseService,
        private fileUploadExerciseService: FileUploadExerciseService,
        private courseExerciseService: CourseExerciseService,
        private alertService: AlertService,
        private accountService: AccountService,
        private modalService: NgbModal,
        private router: Router,
        private sortService: SortService,
        courseService: CourseManagementService,
        translateService: TranslateService,
        eventManager: EventManager,
        route: ActivatedRoute,
    ) {
        super(courseService, translateService, route, eventManager);
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
                        this.selectedFileUploadExercises = [];
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
     * @param index of a file upload exercise in the collection
     * @param item current file upload exercise
     */
    trackId(index: number, item: FileUploadExercise) {
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

    /**
     * Deletes all the given file upload exercises
     * @param exercisesToDelete the exercise objects which are to be deleted
     * @param event contains additional checks which are performed for all these exercises
     */
    deleteMultipleFileUploadExercises(exercisesToDelete: FileUploadExercise[]) {
        const deletionObservables = exercisesToDelete.map((exercise) => this.fileUploadExerciseService.delete(exercise.id!));
        return merge(...deletionObservables).subscribe({
            next: () => {
                this.eventManager.broadcast({
                    name: 'fileUploadExerciseListModification',
                    content: 'Deleted selected Exercises',
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

    toggleFileUploadExercise(fileUploadExercise: FileUploadExercise) {
        const fileUploadExerciseIndex = this.selectedFileUploadExercises.indexOf(fileUploadExercise);
        if (fileUploadExerciseIndex !== -1) {
            this.selectedFileUploadExercises.splice(fileUploadExerciseIndex, 1);
        } else {
            this.selectedFileUploadExercises.push(fileUploadExercise);
        }
    }

    toggleAllFileUploadExercises() {
        this.selectedFileUploadExercises = [];
        if (!this.allChecked) {
            this.selectedFileUploadExercises = this.selectedFileUploadExercises.concat(this.fileUploadExercises);
        }
        this.allChecked = !this.allChecked;
    }

    isExerciseSelected(exercise: FileUploadExercise) {
        return this.selectedFileUploadExercises.includes(exercise);
    }
}
