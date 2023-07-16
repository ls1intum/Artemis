import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, Subscription } from 'rxjs';
import { Course, isCommunicationEnabled, isMessagingEnabled } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ButtonSize } from 'app/shared/components/button.component';
import { EventManager } from 'app/core/util/event-manager.service';
import {
    faArrowUpRightFromSquare,
    faChartBar,
    faClipboard,
    faComment,
    faComments,
    faEye,
    faFilePdf,
    faFlag,
    faGraduationCap,
    faListAlt,
    faPersonChalkboard,
    faTable,
    faTimes,
    faUserCheck,
    faWrench,
} from '@fortawesome/free-solid-svg-icons';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { CourseAdminService } from 'app/course/manage/course-admin.service';

@Component({
    selector: 'jhi-course-management-tab-bar',
    templateUrl: './course-management-tab-bar.component.html',
    styleUrls: ['./course-management-tab-bar.component.scss'],
})
export class CourseManagementTabBarComponent implements OnInit, OnDestroy {
    readonly FeatureToggle = FeatureToggle;
    readonly ButtonSize = ButtonSize;

    course?: Course;

    private paramSub?: Subscription;
    private courseSub?: Subscription;
    private eventSubscriber: Subscription;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    // Icons
    faArrowUpRightFromSquare = faArrowUpRightFromSquare;
    faTimes = faTimes;
    faEye = faEye;
    faWrench = faWrench;
    faTable = faTable;
    faUserCheck = faUserCheck;
    faFlag = faFlag;
    faListAlt = faListAlt;
    faChartBar = faChartBar;
    faFilePdf = faFilePdf;
    faComment = faComment;
    faComments = faComments;
    faClipboard = faClipboard;
    faGraduationCap = faGraduationCap;
    faPersonChalkboard = faPersonChalkboard;

    readonly isCommunicationEnabled = isCommunicationEnabled;
    readonly isMessagingEnabled = isMessagingEnabled;

    constructor(
        private eventManager: EventManager,
        private courseManagementService: CourseManagementService,
        private courseAdminService: CourseAdminService,
        private route: ActivatedRoute,
        private router: Router,
    ) {}

    /**
     * On init load the course information and subscribe to listen for changes in course.
     */
    ngOnInit() {
        let courseId = 0;
        this.paramSub = this.route.firstChild?.params.subscribe((params) => {
            courseId = params['courseId'];
            this.subscribeToCourseUpdates(courseId);
        });

        // Subscribe to course modifications and reload the course after a change.
        this.eventSubscriber = this.eventManager.subscribe('courseModification', () => {
            this.subscribeToCourseUpdates(courseId);
        });
    }

    /**
     * Subscribe to changes in course.
     */
    private subscribeToCourseUpdates(courseId: number) {
        this.courseSub = this.courseManagementService.find(courseId).subscribe((courseResponse) => {
            this.course = courseResponse.body!;
        });
    }

    /**
     * On destroy unsubscribe all subscriptions.
     */
    ngOnDestroy() {
        if (this.paramSub) {
            this.paramSub.unsubscribe();
        }
        if (this.courseSub) {
            this.courseSub.unsubscribe();
        }
        this.eventManager.destroy(this.eventSubscriber);
    }

    /**
     * Deletes the course
     * @param courseId id the course that will be deleted
     */
    deleteCourse(courseId: number) {
        this.courseAdminService.delete(courseId).subscribe({
            next: () => {
                this.eventManager.broadcast({
                    name: 'courseListModification',
                    content: 'Deleted an course',
                });
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
        this.router.navigate(['/course-management']);
    }

    shouldHighlightTutorialsLink(): boolean {
        const tutorialsRegex = /tutorial-groups/;
        return tutorialsRegex.test(this.router.url);
    }

    shouldHighlightAssessmentLink(): boolean {
        const assessmentLinkRegex = /scores|grading-system|plagiarism-cases/;
        return assessmentLinkRegex.test(this.router.url);
    }

    shouldShowControlButtons(): boolean {
        const courseManagementRegex = /course-management\/[0-9]+(\/edit)?$/;
        return courseManagementRegex.test(this.router.url);
    }
}
