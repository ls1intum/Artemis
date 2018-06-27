import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiAlertService, JhiEventManager, JhiParseLinks } from 'ng-jhipster';

import { ProgrammingExercise } from './programming-exercise.model';
import { ProgrammingExerciseService } from './programming-exercise.service';
import { ITEMS_PER_PAGE, Principal } from '../../shared';
import { Course, CourseProgrammingExerciseService, CourseService } from '../course';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-programming-exercise',
    templateUrl: './programming-exercise.component.html'
})
export class ProgrammingExerciseComponent implements OnInit, OnDestroy {
    private subscription: Subscription;
    programmingExercises: ProgrammingExercise[];
    course: Course;
    eventSubscriber: Subscription;
    courseId: number;
    itemsPerPage: number;
    links: any;
    page: any;
    predicate: any;
    reverse: any;
    totalItems: number;

    constructor(
        private programmingExerciseService: ProgrammingExerciseService,
        private courseProgrammingExerciseService: CourseProgrammingExerciseService,
        private courseService: CourseService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private parseLinks: JhiParseLinks,
        private principal: Principal,
        private route: ActivatedRoute
    ) {
        this.programmingExercises = [];
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
            this.registerChangeInProgrammingExercises();
        });
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    loadAll() {
        this.programmingExerciseService.query().subscribe(
            (res: HttpResponse<ProgrammingExercise[]>) => {
                this.programmingExercises = res.body;
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
        this.courseProgrammingExerciseService.query(this.courseId, {
            page: this.page,
            size: this.itemsPerPage
        }).subscribe(
            (res: HttpResponse<ProgrammingExercise[]>) => {
                this.programmingExercises = res.body;
            },
            (res: HttpResponse<ProgrammingExercise>[]) => this.onError(res)
        );
        this.courseService.find(this.courseId).subscribe(res => {
            this.course = res.body;
        });
    }

    loadPage(page) {
        this.page = page;
        this.loadAll();
    }

    trackId(index: number, item: ProgrammingExercise) {
        return item.id;
    }
    registerChangeInProgrammingExercises() {
        this.eventSubscriber = this.eventManager.subscribe('programmingExerciseListModification', response => this.load());
    }

    private onError(error) {
        this.jhiAlertService.error(error.message, null, null);
    }

    callback() { }
}
