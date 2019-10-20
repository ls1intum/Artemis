import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';
import { Course } from './course.model';
import { CourseService } from './course.service';
import { OnError } from 'app/shared/util/on-error';

@Component({
    selector: 'jhi-course',
    templateUrl: './course.component.html',
    styles: ['.course-table {padding-bottom: 5rem}'],
})
export class CourseComponent extends OnError implements OnInit, OnDestroy {
    predicate: string;
    reverse: boolean;
    showOnlyActive = true;

    courses: Course[];
    eventSubscriber: Subscription;
    closeDialogTrigger: boolean;

    constructor(private courseService: CourseService, protected jhiAlertService: JhiAlertService, private eventManager: JhiEventManager) {
        super(jhiAlertService);
        this.predicate = 'id';
        // show the newest courses first and the oldest last
        this.reverse = false;
    }

    loadAll() {
        this.courseService.query().subscribe(
            (res: HttpResponse<Course[]>) => {
                this.courses = res.body!;
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
                this.closeDialogTrigger = !this.closeDialogTrigger;
            },
            (error: HttpErrorResponse) => this.onError(error),
        );
    }

    callback() {}

    get today(): Date {
        return new Date();
    }
}
