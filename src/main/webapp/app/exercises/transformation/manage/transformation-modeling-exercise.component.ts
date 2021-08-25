import { Component, Input } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { AccountService } from 'app/core/auth/account.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ActivatedRoute, Router } from '@angular/router';
import { CourseExerciseService, CourseManagementService } from 'app/course/manage/course-management.service';
import { ExerciseComponent } from 'app/exercises/shared/exercise/exercise.component';
import { TranslateService } from '@ngx-translate/core';
import { onError } from 'app/shared/util/global.utils';
import { SortService } from 'app/shared/service/sort.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { TransformationModelingExercise } from 'app/entities/transformation-modeling-exercise.model';
import { ModelingExerciseService } from 'app/exercises/modeling/manage/modeling-exercise.service';

@Component({
    selector: 'jhi-transformation-modeling-exercise',
    templateUrl: './transformation-modeling-exercise.component.html',
})
export class TransformationModelingExerciseComponent extends ExerciseComponent {
    @Input() transformationModelingExercises: TransformationModelingExercise[];

    constructor(
        public exerciseService: ExerciseService,
        private transformationModelingExerciseService: ModelingExerciseService,
        private courseExerciseService: CourseExerciseService,
        private jhiAlertService: JhiAlertService,
        private accountService: AccountService,
        private sortService: SortService,
        private modalService: NgbModal,
        private router: Router,
        courseService: CourseManagementService,
        translateService: TranslateService,
        eventManager: JhiEventManager,
        route: ActivatedRoute,
    ) {
        super(courseService, translateService, route, eventManager);
        this.transformationModelingExercises = [];
    }

    protected loadExercises(): void {
        this.courseExerciseService.findAllTransformationModelingExercisesForCourse(this.courseId).subscribe(
            (res: HttpResponse<TransformationModelingExercise[]>) => {
                this.transformationModelingExercises = res.body!;
                // reconnect exercise with course
                this.transformationModelingExercises.forEach((exercise) => {
                    exercise.course = this.course;
                    exercise.isAtLeastTutor = this.accountService.isAtLeastTutorInCourse(exercise.course);
                    exercise.isAtLeastEditor = this.accountService.isAtLeastEditorInCourse(exercise.course);
                    exercise.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(exercise.course);
                });
                this.emitExerciseCount(this.transformationModelingExercises.length);
            },
            (res: HttpErrorResponse) => onError(this.jhiAlertService, res),
        );
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
        this.transformationModelingExerciseService.delete(modelingExerciseId).subscribe(
            () => {
                this.eventManager.broadcast({
                    name: 'transformationModelingExerciseListModification',
                    content: 'Deleted a transformationModelingExercise',
                });
                this.dialogErrorSource.next('');
            },
            (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        );
    }

    protected getChangeEventName(): string {
        return 'modelingExerciseListModification';
    }

    sortRows() {
        this.sortService.sortByProperty(this.transformationModelingExercises, this.predicate, this.reverse);
    }

    /**
     * Used in the template for jhiSort
     */
    callback() {}
}
