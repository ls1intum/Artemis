import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';

import { FileUploadExercise } from './file-upload-exercise.model';
import { FileUploadExerciseService } from './file-upload-exercise.service';
import { ITEMS_PER_PAGE } from '../../shared';
import { Course, CourseExerciseService, CourseService } from '../course';
import { ActivatedRoute } from '@angular/router';

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
    itemsPerPage: number;
    links: any;
    page: any;
    predicate: any;
    reverse: any;

    constructor(
        private fileUploadExerciseService: FileUploadExerciseService,
        private courseExerciseService: CourseExerciseService,
        private courseService: CourseService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private route: ActivatedRoute
    ) {
        this.fileUploadExercises = [];
        this.itemsPerPage = ITEMS_PER_PAGE;
        this.page = 0;
        this.links = {
            last: 0
        };
        this.predicate = 'id';
        this.reverse = true;
    }

    ngOnInit() {
        this.subscription = this.route.params.subscribe(params => {
            this.load();
            this.registerChangeInFileUploadExercises();
        });
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    loadAll() {
        this.fileUploadExerciseService.query().subscribe(
            (res: HttpResponse<FileUploadExercise[]>) => {
                this.fileUploadExercises = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    load() {
        this.subscription = this.route.params.subscribe(params => {
            this.courseId = params['courseId'];
            if (this.courseId) {
                this.loadAllForCourse();
            } else {
                this.loadAll();
            }
        });
    }

    loadAllForCourse() {
        this.courseExerciseService.findAllFileUploadExercises(this.courseId, {
            page: this.page,
            size: this.itemsPerPage
        }).subscribe(
            (res: HttpResponse<FileUploadExercise[]>) => {
                this.fileUploadExercises = res.body;
            },
            (res: HttpResponse<FileUploadExercise>[]) => this.onError(res)
        );
        this.courseService.find(this.courseId).subscribe(res => {
            this.course = res.body;
        });
    }

    loadPage(page) {
        this.page = page;
        this.loadAll();
    }

    trackId(index: number, item: FileUploadExercise) {
        return item.id;
    }
    registerChangeInFileUploadExercises() {
        this.eventSubscriber = this.eventManager.subscribe('fileUploadExerciseListModification', response => this.load());
    }

    private onError(error) {
        this.jhiAlertService.error(error.message, null, null);
    }

    callback() { }
}
