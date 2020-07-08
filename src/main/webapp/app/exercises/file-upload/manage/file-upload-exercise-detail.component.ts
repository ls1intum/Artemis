import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager } from 'ng-jhipster';

import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { FileUploadExerciseService } from './file-upload-exercise.service';
import { filter } from 'rxjs/operators';
import { AlertService } from 'app/core/alert/alert.service';

@Component({
    selector: 'jhi-file-upload-exercise-detail',
    templateUrl: './file-upload-exercise-detail.component.html',
})
export class FileUploadExerciseDetailComponent implements OnInit, OnDestroy {
    fileUploadExercise: FileUploadExercise;
    isExamExercise: boolean;
    private subscription: Subscription;
    private eventSubscriber: Subscription;

    constructor(
        private eventManager: JhiEventManager,
        private fileUploadExerciseService: FileUploadExerciseService,
        private route: ActivatedRoute,
        private jhiAlertService: AlertService,
    ) {}

    /**
     * Initializes subscription for file upload exercise
     */
    ngOnInit() {
        // TODO: route determines whether the component is in exam mode
        this.subscription = this.route.params.subscribe((params) => {
            this.load(params['exerciseId']);
        });
        this.registerChangeInFileUploadExercises();
    }

    /**
     * Loads file upload exercise from the server
     * @param exerciseId the id of the file upload exercise
     */
    load(exerciseId: number) {
        // TODO: Use a separate find method for exam exercises containing course, exam, exerciseGroup and exercise id
        this.fileUploadExerciseService
            .find(exerciseId)
            .pipe(filter((res) => !!res.body))
            .subscribe(
                (fileUploadExerciseResponse: HttpResponse<FileUploadExercise>) => {
                    this.fileUploadExercise = fileUploadExerciseResponse.body!;
                    this.isExamExercise = !!this.fileUploadExercise.exerciseGroup;
                    if (this.fileUploadExercise.categories) {
                        this.fileUploadExercise.categories = this.fileUploadExercise.categories.map((category) => JSON.parse(category));
                    }
                },
                (res: HttpErrorResponse) => this.onError(res),
            );
    }

    /**
     * Returns the route for editing the exercise. Exam and course exercises have different routes.
     */
    getEditRoute() {
        if (this.isExamExercise) {
            return [
                '/course-management',
                this.fileUploadExercise.exerciseGroup?.exam?.course?.id,
                'exams',
                this.fileUploadExercise.exerciseGroup?.exam?.id,
                'exercise-groups',
                this.fileUploadExercise.exerciseGroup?.id,
                'file-upload-exercises',
                this.fileUploadExercise.id,
                'edit',
            ];
        } else {
            return ['/course-management', this.fileUploadExercise.course?.id, 'file-upload-exercises', this.fileUploadExercise.id, 'edit'];
        }
    }

    /**
     * Returns to previous state (same behaviour as back button in the browser)
     */
    previousState() {
        window.history.back();
    }

    /**
     * Unsubscribes on component destruction
     */
    ngOnDestroy() {
        this.subscription.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    /**
     * Listens to file upload exercise list modifications
     */
    registerChangeInFileUploadExercises() {
        this.eventSubscriber = this.eventManager.subscribe('fileUploadExerciseListModification', () => this.load(this.fileUploadExercise.id));
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message);
    }
}
