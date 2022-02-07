import { Component, Input } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ModelingExerciseService } from './modeling-exercise.service';
import { AccountService } from 'app/core/auth/account.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ActivatedRoute, Router } from '@angular/router';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ExerciseComponent } from 'app/exercises/shared/exercise/exercise.component';
import { TranslateService } from '@ngx-translate/core';
import { onError } from 'app/shared/util/global.utils';
import { SortService } from 'app/shared/service/sort.service';
import { ModelingExerciseImportComponent } from 'app/exercises/modeling/manage/modeling-exercise-import.component';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { AlertService } from 'app/core/util/alert.service';
import { EventManager } from 'app/core/util/event-manager.service';
import { faBook, faPlus, faSort, faTable, faTimes, faUsers, faWrench } from '@fortawesome/free-solid-svg-icons';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';

@Component({
    selector: 'jhi-modeling-exercise',
    templateUrl: './modeling-exercise.component.html',
})
export class ModelingExerciseComponent extends ExerciseComponent {
    @Input() modelingExercises: ModelingExercise[];
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

    constructor(
        public exerciseService: ExerciseService,
        private modelingExerciseService: ModelingExerciseService,
        private courseExerciseService: CourseExerciseService,
        private alertService: AlertService,
        private accountService: AccountService,
        private sortService: SortService,
        private modalService: NgbModal,
        private router: Router,
        courseService: CourseManagementService,
        translateService: TranslateService,
        eventManager: EventManager,
        route: ActivatedRoute,
    ) {
        super(courseService, translateService, route, eventManager);
        this.modelingExercises = [];
    }

    protected loadExercises(): void {
        this.courseExerciseService.findAllModelingExercisesForCourse(this.courseId).subscribe({
            next: (res: HttpResponse<ModelingExercise[]>) => {
                this.modelingExercises = res.body!;
                // reconnect exercise with course
                this.modelingExercises.forEach((exercise) => {
                    exercise.course = this.course;
                    this.accountService.setAccessRightsForExercise(exercise);
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

    openImportModal() {
        const modalRef = this.modalService.open(ModelingExerciseImportComponent, { size: 'lg', backdrop: 'static' });
        modalRef.result.then(
            (result: ModelingExercise) => {
                this.router.navigate(['course-management', this.courseId, 'modeling-exercises', result.id, 'import']);
            },
            () => {},
        );
    }
}
