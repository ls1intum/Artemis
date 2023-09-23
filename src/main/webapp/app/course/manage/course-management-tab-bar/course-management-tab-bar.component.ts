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
    faNetworkWired,
    faPersonChalkboard,
    faRobot,
    faTable,
    faTimes,
    faUserCheck,
    faWrench,
} from '@fortawesome/free-solid-svg-icons';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { CourseAdminService } from 'app/course/manage/course-admin.service';
import { IrisCourseSettingsUpdateComponent } from 'app/iris/settings/iris-course-settings-update/iris-course-settings-update.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';

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
    faNetworkWired = faNetworkWired;
    faListAlt = faListAlt;
    faChartBar = faChartBar;
    faFilePdf = faFilePdf;
    faComment = faComment;
    faComments = faComments;
    faClipboard = faClipboard;
    faGraduationCap = faGraduationCap;
    faPersonChalkboard = faPersonChalkboard;
    faRobot = faRobot;

    readonly isCommunicationEnabled = isCommunicationEnabled;
    readonly isMessagingEnabled = isMessagingEnabled;

    irisEnabled = false;

    constructor(
        private eventManager: EventManager,
        private courseManagementService: CourseManagementService,
        private courseAdminService: CourseAdminService,
        private route: ActivatedRoute,
        private router: Router,
        private modalService: NgbModal,
        private profileService: ProfileService,
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

        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo) {
                this.irisEnabled = profileInfo.activeProfiles.includes('iris');
            }
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

    /**
     * Checks if the current route contains 'tutorial-groups'.
     * @return true if the current route is part of the tutorial management
     */
    shouldHighlightTutorialsLink(): boolean {
        const tutorialsRegex = /tutorial-groups/;
        return tutorialsRegex.test(this.router.url);
    }

    /**
     * Checks if the current route contains 'grading-system' or 'plagiarism-cases' but not 'exams'.
     * @return true if the current route is part of the assessment management
     */
    shouldHighlightAssessmentLink(): boolean {
        // Exclude exam related links from the assessment link highlighting.
        // Example that should not highlight the assessment link: /course-management/{courseId}/exams/{examId}/grading-system/interval
        const assessmentLinkRegex = /^(?!.*exams).*(grading-system|plagiarism-cases|assessment-dashboard)/;
        return assessmentLinkRegex.test(this.router.url);
    }

    /**
     * Checks if the current route is 'course-management/{courseId}/edit' or 'course-management/{courseId}'.
     * @return true if the control buttons, e.g., delete & edit, should be shown
     */
    shouldShowControlButtons(): boolean {
        const courseManagementRegex = /course-management\/[0-9]+(\/edit)?$/;
        return courseManagementRegex.test(this.router.url);
    }

    /**
     * Shows the iris settings in a modal.
     */
    showIrisSettings(): void {
        const modalRef = this.modalService.open(IrisCourseSettingsUpdateComponent, { size: 'xl' });
        modalRef.componentInstance.courseId = this.course!.id;
    }
}
