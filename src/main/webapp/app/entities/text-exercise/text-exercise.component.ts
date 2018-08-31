import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';

import { TextExercise } from './text-exercise.model';
import { TextExerciseService } from './text-exercise.service';
import { ITEMS_PER_PAGE } from '../../shared';
import { Course, CourseExerciseService, CourseService } from '../course';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-text-exercise',
    templateUrl: './text-exercise.component.html'
})
export class TextExerciseComponent implements OnInit, OnDestroy {
    private subscription: Subscription;
    textExercises: TextExercise[];
    course: Course;
    eventSubscriber: Subscription;
    courseId: number;
    itemsPerPage: number;
    links: any;
    page: any;
    predicate: any;
    reverse: any;

    constructor(
        private textExerciseService: TextExerciseService,
        private courseExerciseService: CourseExerciseService,
        private courseService: CourseService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private route: ActivatedRoute
    ) {
        this.textExercises = [];
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
            this.registerChangeInTextExercises();
        });
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    loadAll() {
        this.textExerciseService.query().subscribe(
            (res: HttpResponse<TextExercise[]>) => {
                this.textExercises = res.body;
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
        this.courseExerciseService.findAllTextExercises(this.courseId, {
            page: this.page,
            size: this.itemsPerPage
        }).subscribe(
            (res: HttpResponse<TextExercise[]>) => {
                this.textExercises = res.body;
            },
            (res: HttpResponse<TextExercise>[]) => this.onError(res)
        );
        this.courseService.find(this.courseId).subscribe(res => {
            this.course = res.body;
        });
    }

    loadPage(page) {
        this.page = page;
        this.loadAll();
    }

    trackId(index: number, item: TextExercise) {
        return item.id;
    }
    registerChangeInTextExercises() {
        this.eventSubscriber = this.eventManager.subscribe('textExerciseListModification', response => this.load());
    }

    private onError(error) {
        this.jhiAlertService.error(error.message, null, null);
    }

    callback() { }
}
