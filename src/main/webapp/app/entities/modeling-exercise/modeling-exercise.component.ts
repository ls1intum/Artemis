import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';

import { ModelingExercise } from './modeling-exercise.model';
import { ModelingExerciseService } from './modeling-exercise.service';
import { ITEMS_PER_PAGE } from '../../shared';
import { Principal } from '../../core';
import { CourseExerciseService } from '../course/course.service';
import { ActivatedRoute } from '@angular/router';
import { Course, CourseService } from '../course';

@Component({
    selector: 'jhi-modeling-exercise',
    templateUrl: './modeling-exercise.component.html'
})
export class ModelingExerciseComponent implements OnInit, OnDestroy {
    private subscription: Subscription;
    modelingExercises: ModelingExercise[];
    course: Course;
    eventSubscriber: Subscription;
    courseId: number;
    itemsPerPage: number;
    links: any;
    page: number;
    predicate: string;
    reverse: boolean;

    constructor(
        private modelingExerciseService: ModelingExerciseService,
        private courseExerciseService: CourseExerciseService,
        private courseService: CourseService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal,
        private route: ActivatedRoute
    ) {
        this.modelingExercises = [];
        this.itemsPerPage = ITEMS_PER_PAGE;
        this.page = 0;
        this.links = {
            last: 0
        };
        this.predicate = 'id';
        this.reverse = true;
    }

    ngOnInit() {
        this.load();
        this.registerChangeInModelingExercises();
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

    loadAll() {
        this.modelingExerciseService.query().subscribe(
            (res: HttpResponse<ModelingExercise[]>) => {
                this.modelingExercises = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res)
        );
    }

    loadAllForCourse() {
        this.courseExerciseService
            .findAllModelingExercises(this.courseId, {
                page: this.page,
                size: this.itemsPerPage
            })
            .subscribe(
                (res: HttpResponse<ModelingExercise[]>) => {
                    this.modelingExercises = res.body;
                },
                (res: HttpErrorResponse) => this.onError(res)
            );
        this.courseService.find(this.courseId).subscribe(res => {
            this.course = res.body;
        });
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    loadPage(page: number) {
        this.page = page;
        this.loadAll();
    }

    trackId(index: number, item: ModelingExercise) {
        return item.id;
    }
    registerChangeInModelingExercises() {
        this.eventSubscriber = this.eventManager.subscribe('modelingExerciseListModification', () => this.load());
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message, null, null);
        console.log('Error: ' + error);
    }

    callback() {}
}
