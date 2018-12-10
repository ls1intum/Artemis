import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';

import { FileUploadExercise } from './file-upload-exercise.model';
import { FileUploadExerciseService } from './file-upload-exercise.service';
import { ITEMS_PER_PAGE } from '../../shared';
import { Course, CourseExerciseService, CourseService } from '../course';
import { ActivatedRoute } from '@angular/router';
import { fileUploadExerciseRoute } from 'app/entities/file-upload-exercise/file-upload-exercise.route';

@Component({
    selector: 'jhi-file-upload-exercise',
    templateUrl: './file-upload-exercise.component.html'
})
export class FileUploadExerciseComponent implements OnInit, OnDestroy {
    private subscription: Subscription;
    fileUploadExercises: FileUploadExercise[];
    course: Course;
    eventSubscriber: Subscription;
    courseId: number;
    predicate: string;
    reverse: boolean;
    @Input() showHeading = true;

    constructor(
        private fileUploadExerciseService: FileUploadExerciseService,
        private courseExerciseService: CourseExerciseService,
        private courseService: CourseService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private route: ActivatedRoute
    ) {
        this.fileUploadExercises = [];
        this.predicate = 'id';
        this.reverse = true;
    }

    ngOnInit() {
        this.subscription = this.route.params.subscribe(params => {
            this.load();
            this.registerChangeInFileUploadExercises();
        });
    }

    load() {
        this.subscription = this.route.params.subscribe(params => {
            this.courseId = params['courseId'];
            this.loadForCourse();
        });
    }

    loadForCourse() {
        this.courseService.find(this.courseId).subscribe(courseResponse => {
            this.course = courseResponse.body;
            this.courseExerciseService.findAllFileUploadExercisesForCourse(this.courseId).subscribe(
                (res: HttpResponse<FileUploadExercise[]>) => {
                    this.fileUploadExercises = res.body;
                    // reconnect exercise with course
                    this.fileUploadExercises.forEach(fileUploadExercise => {
                        fileUploadExercise.course = this.course;
                    });
                },
                (res: HttpErrorResponse) => this.onError(res)
            );
        });
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: FileUploadExercise) {
        return item.id;
    }
    registerChangeInFileUploadExercises() {
        this.eventSubscriber = this.eventManager.subscribe('fileUploadExerciseListModification', () => this.load());
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message);
    }

    callback() {}
}
