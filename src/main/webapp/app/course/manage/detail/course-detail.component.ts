import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from '../course-management.service';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { Subject } from 'rxjs';
import { ButtonSize } from 'app/shared/components/button.component';
import { CourseManagementDetailViewDto } from 'app/course/manage/course-management-detail-view-dto.model';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { EventManager } from 'app/core/util/event-manager.service';
import {
    faChartBar,
    faClipboard,
    faComments,
    faEye,
    faFilePdf,
    faFlag,
    faGraduationCap,
    faHeartBroken,
    faListAlt,
    faTable,
    faTimes,
    faUserCheck,
    faWrench,
    faPersonChalkboard,
} from '@fortawesome/free-solid-svg-icons';

export enum DoughnutChartType {
    ASSESSMENT = 'ASSESSMENT',
    COMPLAINTS = 'COMPLAINTS',
    FEEDBACK = 'FEEDBACK',
    AVERAGE_COURSE_SCORE = 'AVERAGE_COURSE_SCORE',
    AVERAGE_EXERCISE_SCORE = 'AVERAGE_EXERCISE_SCORE',
    PARTICIPATIONS = 'PARTICIPATIONS',
    QUESTIONS = 'QUESTIONS',
}

@Component({
    selector: 'jhi-course-detail',
    templateUrl: './course-detail.component.html',
    styleUrls: ['./course-detail.component.scss'],
})
export class CourseDetailComponent implements OnInit, OnDestroy {
    readonly DoughnutChartType = DoughnutChartType;

    ButtonSize = ButtonSize;
    ActionType = ActionType;

    courseDTO: CourseManagementDetailViewDto;
    activeStudents: number[];
    course: Course;

    private eventSubscriber: Subscription;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();
    paramSub: Subscription;

    // Icons
    faTimes = faTimes;
    faEye = faEye;
    faWrench = faWrench;
    faTable = faTable;
    faUserCheck = faUserCheck;
    faFlag = faFlag;
    faListAlt = faListAlt;
    faChartBar = faChartBar;
    faFilePdf = faFilePdf;
    faComments = faComments;
    faClipboard = faClipboard;
    faGraduationCap = faGraduationCap;
    faHeartBroken = faHeartBroken;
    faPersonChalkboard = faPersonChalkboard;

    constructor(
        private eventManager: EventManager,
        private courseService: CourseManagementService,
        private route: ActivatedRoute,
        private router: Router,
        private alertService: AlertService,
    ) {}

    /**
     * On init load the course information and subscribe to listen for changes in courses.
     */
    ngOnInit() {
        this.fetchData();
        this.registerChangeInCourses();
    }

    /**
     * Subscribe to changes in courses and reload the course after a change.
     */
    registerChangeInCourses() {
        this.eventSubscriber = this.eventManager.subscribe('courseListModification', () => {
            this.fetchData();
        });
    }

    /**
     * On destroy unsubscribe all subscriptions.
     */
    ngOnDestroy() {
        if (this.paramSub) {
            this.paramSub.unsubscribe();
        }
        this.eventManager.destroy(this.eventSubscriber);
    }

    /**
     * fetch the course and course specific statistics for the page
     */
    private fetchData() {
        // There is no course 0 -> will fetch no course if route does not provide different courseId
        let courseId = 0;
        this.paramSub = this.route.params.subscribe((params) => {
            courseId = params['courseId'];
        });
        // Get course first for basic course information
        this.courseService.find(courseId).subscribe((courseResponse) => {
            this.course = courseResponse.body!;
        });
        // fetch statistics separately because it takes quite long for larger courses
        this.courseService.getCourseStatisticsForDetailView(courseId).subscribe({
            next: (courseResponse: HttpResponse<CourseManagementDetailViewDto>) => {
                this.courseDTO = courseResponse.body!;
                this.activeStudents = courseResponse.body!.activeStudents;
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }

    /**
     * Deletes the course
     * @param courseId id the course that will be deleted
     */
    deleteCourse(courseId: number) {
        this.courseService.delete(courseId).subscribe({
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
}
