import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager } from 'ng-jhipster';

import { Course } from './course.model';
import { CourseService } from './course.service';

@Component({
    selector: 'jhi-course-detail',
    templateUrl: './course-detail.component.html'
})
export class CourseDetailComponent implements OnInit, OnDestroy {

    course: Course;
    private subscription: Subscription;
    private eventSubscriber: Subscription;

    constructor(
        private eventManager: JhiEventManager,
        private courseService: CourseService,
        private route: ActivatedRoute
    ) {
    }

    ngOnInit() {
        this.subscription = this.route.params.subscribe((params) => {
            this.load(params['id']);
        });
        this.registerChangeInCourses();
    }

    load(id) {
        this.courseService.find(id)
            .subscribe((courseResponse: HttpResponse<Course>) => {
                this.course = courseResponse.body;
            });
    }
    previousState() {
        window.history.back();
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    registerChangeInCourses() {
        this.eventSubscriber = this.eventManager.subscribe(
            'courseListModification',
            (response) => this.load(this.course.id)
        );
    }
}
