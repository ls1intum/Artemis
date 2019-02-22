import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

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
        private route: ActivatedRoute,
        private jhiAlertService: JhiAlertService
    ) {
    }

    ngOnInit() {
        this.subscription = this.route.params.subscribe(params => {
            this.load(params['id']);
        });
        this.registerChangeInCourses();
    }

    load(courseId: number) {
        this.courseService.find(courseId)
            .subscribe((courseResponse: HttpResponse<Course>) => {
                this.course = courseResponse.body;
            });
    }

    registerForCourse() {
        this.courseService.registerForCourse(this.course.id).subscribe(userResponse => {
            if (userResponse.body != null) {
                const message = 'Registered user for course ' + this.course.title;
                const jhiAlert = this.jhiAlertService.info(message);
                jhiAlert.msg = message;
            }
        }, (error: HttpErrorResponse) =>  {
            const errorMessage = error.headers.get('X-arTeMiSApp-message');
            // TODO: this is a workaround to avoid translation not found issues. Provide proper translations
            const jhiAlert = this.jhiAlertService.error(errorMessage);
            jhiAlert.msg = errorMessage;
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
            () => this.load(this.course.id)
        );
    }
}
