import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';
import { Course } from './course.model';
import { CourseService } from './course.service';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { courseAdministrationTour } from 'app/guided-tour/tours/course-administration-tour';

@Component({
    selector: 'jhi-course',
    templateUrl: './course.component.html',
    styles: ['.course-table {padding-bottom: 5rem}'],
})
export class CourseComponent implements OnInit, OnDestroy {
    predicate: string;
    reverse: boolean;
    showOnlyActive = true;

    courses: Course[];
    eventSubscriber: Subscription;

    readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;

    courseForGuidedTour: Course | null;

    constructor(
        private courseService: CourseService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private guidedTourService: GuidedTourService,
    ) {
        this.predicate = 'id';
        // show the newest courses first and the oldest last
        this.reverse = false;
    }

    loadAll() {
        this.courseService.query().subscribe(
            (res: HttpResponse<Course[]>) => {
                this.courses = res.body!;
                this.courseForGuidedTour = this.guidedTourService.enableTourForCourseOverview(this.courses, courseAdministrationTour);
            },
            (res: HttpErrorResponse) => this.onError(res),
        );
    }

    ngOnInit() {
        this.loadAll();
        this.registerChangeInCourses();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: Course) {
        return item.id;
    }

    registerChangeInCourses() {
        this.eventSubscriber = this.eventManager.subscribe('courseListModification', () => this.loadAll());
    }

    /**
     * Deletes the course
     * @param courseId id the course that will be deleted
     */
    deleteCourse(courseId: number) {
        this.courseService.delete(courseId).subscribe(
            () => {
                this.eventManager.broadcast({
                    name: 'courseListModification',
                    content: 'Deleted an course',
                });
            },
            error => this.onError(error),
        );
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message);
    }

    callback() {}

    get today(): Date {
        return new Date();
    }
}
