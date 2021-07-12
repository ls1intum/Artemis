import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from '../course-management.service';
import { CachingStrategy } from 'app/shared/image/secured-image.component';
import { isOrion } from 'app/shared/orion/orion';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { Subject } from 'rxjs';
import { ButtonSize } from 'app/shared/components/button.component';
import { CourseManagementDetailViewDto } from 'app/course/manage/course-management-detail-view-dto.model';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';
import { onError } from 'app/shared/util/global.utils';

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
    readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;
    readonly DoughnutChartType = DoughnutChartType;

    ButtonSize = ButtonSize;
    ActionType = ActionType;
    readonly isOrion = isOrion;
    CachingStrategy = CachingStrategy;

    courseDTO: CourseManagementDetailViewDto;
    activeStudents: number[];
    course: Course;
    private eventSubscriber: Subscription;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();
    paramSub: Subscription;

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
        this.courseService.getCourseStatisticsForDetailView(courseId).subscribe(
            (courseResponse: HttpResponse<CourseManagementDetailViewDto>) => {
                this.courseDTO = courseResponse.body!;
                this.activeStudents = courseResponse.body!.activeStudents;
            },
            (error: HttpErrorResponse) => onError(this.jhiAlertService, error),
        );
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
        this.router.navigate(['/course-management']);
    }
}
