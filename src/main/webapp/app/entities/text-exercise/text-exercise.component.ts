import { Component, Input, OnDestroy, OnInit } from '@angular/core';
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
    predicate: string;
    reverse: boolean;
    @Input() showHeading = true;

    constructor(
        private textExerciseService: TextExerciseService,
        private courseExerciseService: CourseExerciseService,
        private courseService: CourseService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private route: ActivatedRoute
    ) {
        this.textExercises = [];
        this.predicate = 'id';
        this.reverse = true;
    }

    ngOnInit() {
        this.subscription = this.route.params.subscribe(params => {
            this.load();
            this.registerChangeInTextExercises();
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
            this.courseExerciseService.findAllTextExercisesForCourse(this.courseId).subscribe(
                (res: HttpResponse<TextExercise[]>) => {
                    this.textExercises = res.body;
                    // reconnect exercise with course
                    this.textExercises.forEach(textExercise => {
                        textExercise.course = this.course;
                    });
                },
                (res: HttpErrorResponse) => this.onError(res)
            );
        });
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: TextExercise) {
        return item.id;
    }
    registerChangeInTextExercises() {
        this.eventSubscriber = this.eventManager.subscribe('textExerciseListModification', () => this.load());
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message);
    }

    callback() {}
}
