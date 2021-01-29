import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager } from 'ng-jhipster';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from './course-management.service';
import { CachingStrategy } from 'app/shared/image/secured-image.component';
import { JhiAlertService } from 'ng-jhipster';
import * as moment from 'moment';
import { downloadZipFileFromResponse } from 'app/shared/util/download.util';

@Component({
    selector: 'jhi-course-detail',
    templateUrl: './course-detail.component.html',
    styleUrls: ['./course-detail.component.scss'],
})
export class CourseDetailComponent implements OnInit, OnDestroy {
    CachingStrategy = CachingStrategy;
    course: Course;
    courseIsBeingArchived = false;
    private eventSubscriber: Subscription;

    constructor(
        private eventManager: JhiEventManager,
        private courseService: CourseManagementService,
        private route: ActivatedRoute,
        private router: Router,
        private jhiAlertService: JhiAlertService,
    ) {}

    /**
     * On init load the course information and subscribe to listen for changes in courses.
     */
    ngOnInit() {
        this.route.data.subscribe(({ course }) => {
            this.course = course;
        });
        this.registerChangeInCourses();
    }

    /**
     * Register for the currently loaded course.
     */
    registerForCourse() {
        this.courseService.registerForCourse(this.course.id!).subscribe(
            (userResponse) => {
                if (userResponse.body != undefined) {
                    const message = 'Registered user for course ' + this.course.title;
                    const jhiAlert = this.jhiAlertService.info(message);
                    jhiAlert.msg = message;
                }
            },
            (error: HttpErrorResponse) => {
                const errorMessage = error.headers.get('X-artemisApp-message')!;
                // TODO: this is a workaround to avoid translation not found issues. Provide proper translations
                const jhiAlert = this.jhiAlertService.error(errorMessage);
                jhiAlert.msg = errorMessage;
            },
        );
    }

    /**
     * On destroy unsubscribe all subscriptions.
     */
    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    /**
     * Subscribe to changes in courses and reload the course after a change.
     */
    registerChangeInCourses() {
        this.eventSubscriber = this.eventManager.subscribe('courseListModification', () => {
            this.courseService.find(this.course.id!).subscribe((courseResponse: HttpResponse<Course>) => {
                this.course = courseResponse.body!;
            });
        });
    }

    canArchiveCourse() {
        // A course can only be archived if it's over.
        const isCourseOver = this.course.endDate?.isBefore(moment()) ?? false;
        return this.course.isAtLeastInstructor && isCourseOver;
    }

    archiveCourse() {
        this.courseIsBeingArchived = true;
        this.courseService.archiveCourse(this.course.id!).subscribe(
            () => {
                // We don't update the course here because registerChangeInCourses does that already
                this.eventManager.broadcast('courseListModification');
                this.courseIsBeingArchived = false;
            },
            () => {
                this.jhiAlertService.error('artemisApp.course.archive.archiveCourseError');
                this.courseIsBeingArchived = false;
            },
        );
    }

    downloadCourseArchive() {
        this.courseService.downloadCourseArchive(this.course.id!).subscribe(
            (response) => downloadZipFileFromResponse(response),
            () => this.jhiAlertService.error('artemisApp.course.archive.archiveDownloadError'),
        );
    }

    canDownloadArchive() {
        const hasArchive = !!this.course.courseArchivePath && this.course.courseArchivePath.length > 0;
        // You can only download one if the path to the archive is present
        return this.course.isAtLeastInstructor && hasArchive;
    }
}
