import { Component, Input } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';

import { ModelingExercise } from './modeling-exercise.model';
import { ModelingExerciseService } from './modeling-exercise.service';
import { AccountService } from '../../core';
import { CourseExerciseService } from '../course/course.service';
import { ActivatedRoute } from '@angular/router';
import { CourseService } from '../course';
import { ExerciseComponent } from 'app/entities/exercise/exercise.component';
import { TranslateService } from '@ngx-translate/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { DeleteDialogComponent } from 'app/delete-dialog/delete-dialog.component';

@Component({
    selector: 'jhi-modeling-exercise',
    templateUrl: './modeling-exercise.component.html',
})
export class ModelingExerciseComponent extends ExerciseComponent {
    @Input() modelingExercises: ModelingExercise[];
    private ngbModalRef: NgbModalRef | null;

    constructor(
        private modelingExerciseService: ModelingExerciseService,
        private courseExerciseService: CourseExerciseService,
        private jhiAlertService: JhiAlertService,
        private accountService: AccountService,
        private modalService: NgbModal,
        courseService: CourseService,
        translateService: TranslateService,
        eventManager: JhiEventManager,
        route: ActivatedRoute,
    ) {
        super(courseService, translateService, route, eventManager);
        this.modelingExercises = [];
    }

    protected loadExercises(): void {
        this.courseExerciseService.findAllModelingExercisesForCourse(this.courseId).subscribe(
            (res: HttpResponse<ModelingExercise[]>) => {
                this.modelingExercises = res.body!;
                // reconnect exercise with course
                this.modelingExercises.forEach(exercise => {
                    exercise.course = this.course;
                    exercise.isAtLeastTutor = this.accountService.isAtLeastTutorInCourse(exercise.course);
                    exercise.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(exercise.course);
                });
                this.emitExerciseCount(this.modelingExercises.length);
            },
            (res: HttpErrorResponse) => this.onError(res),
        );
    }

    trackId(index: number, item: ModelingExercise) {
        return item.id;
    }

    /**
     * Opens delete modeling exercise popup
     * @param exerciseId the id of exercise
     */
    openDeleteModelingExercisePopup(exerciseId: number) {
        const modalRef = this.modalService.open(DeleteDialogComponent as Component, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.entity = this.modelingExercises.find(exercise => exercise.id === exerciseId);
        modalRef.result.then(
            result => {
                this.modelingExerciseService.delete(exerciseId).subscribe(response => {
                    // @ts-ignore
                    this.eventManager.broadcast({
                        name: 'modelingExerciseListModification',
                        content: 'Deleted an modelingExercise',
                    });
                });
                this.ngbModalRef = null;
            },
            reason => {
                this.ngbModalRef = null;
            },
        );
    }

    protected getChangeEventName(): string {
        return 'modelingExerciseListModification';
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message);
        console.log('Error: ' + error);
    }

    callback() {}
}
