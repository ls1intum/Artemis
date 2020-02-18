import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager } from 'ng-jhipster';
import { Course } from './course.model';
import { CourseService } from './course.service';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';
import { onError } from 'app/utils/global.utils';
import { Subject } from 'rxjs';
import { AlertService } from 'app/core/alert/alert.service';

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

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;

    constructor(private courseService: CourseService, private jhiAlertService: AlertService, private eventManager: JhiEventManager) {
        this.predicate = 'id';
        // show the newest courses first and the oldest last
        this.reverse = false;
    }

    loadAll() {
        this.courseService.query().subscribe(
            (res: HttpResponse<Course[]>) => {
                this.courses = res.body!;
            },
            (res: HttpErrorResponse) => onError(this.jhiAlertService, res),
        );
    }

    ngOnInit() {
        this.loadAll();
        this.registerChangeInCourses();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
        this.dialogErrorSource.unsubscribe();
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
                this.dialogErrorSource.next('');
            },
            (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        );
    }

    callback() {}

    get today(): Date {
        return new Date();
    }
}
