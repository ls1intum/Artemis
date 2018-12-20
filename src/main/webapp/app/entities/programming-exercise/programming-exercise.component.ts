import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';

import { ProgrammingExercise } from './programming-exercise.model';
import { ProgrammingExerciseService } from './programming-exercise.service';
import { ITEMS_PER_PAGE } from '../../shared';
import { Course, CourseExerciseService, CourseService } from '../course';
import { ActivatedRoute } from '@angular/router';
import { programmingExerciseRoute } from 'app/entities/programming-exercise/programming-exercise.route';
import { EMPTY } from 'rxjs';

@Component({
    selector: 'jhi-programming-exercise',
    templateUrl: './programming-exercise.component.html'
})
export class ProgrammingExerciseComponent implements OnInit, OnDestroy {
    private subscription: Subscription;
    @Input() programmingExercises: ProgrammingExercise[];
    @Input() course: Course;
    eventSubscriber: Subscription;
    courseId: number;
    predicate: string;
    reverse: boolean;
    @Input() showHeading = true;

    constructor(
        private programmingExerciseService: ProgrammingExerciseService,
        private courseExerciseService: CourseExerciseService,
        private courseService: CourseService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private route: ActivatedRoute
    ) {
        this.programmingExercises = [];
        this.predicate = 'id';
        this.reverse = true;
    }

    ngOnInit() {
        this.subscription = this.route.params.subscribe(params => {
            this.load();
            this.registerChangeInProgrammingExercises();
        });
    }

    load() {
        if (this.course == null) {
            this.subscription = this.route.params.subscribe(params => {
                this.courseId = params['courseId'];
                this.loadForCourse();
            });
        }
    }

    loadForCourse() {
        this.courseService.find(this.courseId).subscribe(courseResponse => {
            this.course = courseResponse.body;
            this.courseExerciseService.findAllProgrammingExercisesForCourse(this.courseId).subscribe(
                (res: HttpResponse<ProgrammingExercise[]>) => {
                    this.programmingExercises = res.body;
                    // reconnect exercise with course
                    this.programmingExercises.forEach(programmingExercise => {
                        programmingExercise.course = this.course;
                    });
                },
                (res: HttpErrorResponse) => this.onError(res)
            );
        });
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: ProgrammingExercise) {
        return item.id;
    }

    registerChangeInProgrammingExercises() {
        this.eventSubscriber = this.eventManager.subscribe('programmingExerciseListModification', () => this.load());
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message);
    }

    callback() {}
}
