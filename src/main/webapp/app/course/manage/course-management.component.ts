import { AfterViewInit, Component, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager } from 'ng-jhipster';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from './course-management.service';
import { onError } from 'app/shared/util/global.utils';
import { Subject } from 'rxjs';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { tutorAssessmentTour } from 'app/guided-tour/tours/tutor-assessment-tour';
import { JhiAlertService } from 'ng-jhipster';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { LectureService } from 'app/lecture/lecture.service';
import { CourseManagementOverviewDto } from 'app/course/manage/overview/course-management-overview-dto.model';
import { CourseManagementOverviewStatisticsDto } from 'app/course/manage/overview/course-management-overview-statistics-dto.model';

@Component({
    selector: 'jhi-course',
    templateUrl: './course-management.component.html',
    styles: ['.course-table {padding-bottom: 5rem}'],
    styleUrls: ['./course-management.component.scss'],
})
export class CourseManagementComponent implements OnInit, OnDestroy, AfterViewInit {
    predicate: string;
    reverse: boolean;
    showOnlyActive = true;

    courses: Course[];
    details = new Map<number, CourseManagementOverviewDto>();
    statistics = new Map<number, CourseManagementOverviewStatisticsDto>();
    coursesWithUsers = new Map<number, Course>();
    courseSemesters: string[];
    semesterCollapsed: { [key: string]: boolean };
    coursesBySemester: { [key: string]: Course[] };
    eventSubscriber: Subscription;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    courseForGuidedTour?: Course;

    constructor(
        private courseService: CourseManagementService,
        private examService: ExamManagementService,
        private lectureService: LectureService,
        private courseManagementService: CourseManagementService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private guidedTourService: GuidedTourService,
    ) {
        this.predicate = 'id';
        // show the newest courses first and the oldest last
        this.reverse = false;
    }

    /**
     * loads all courses from courseService
     */
    loadAll() {
        this.courseService.getCourseOverview({ onlyActive: this.showOnlyActive }).subscribe(
            (res: HttpResponse<Course[]>) => {
                this.courses = res.body!;
                this.courseSemesters = this.courses
                    // test courses get their own section later
                    .filter((course) => !course.testCourse)
                    .map((course) => course.semester ?? '')
                    // filter down to unique values
                    .filter((course, index, courses) => courses.indexOf(course) === index)
                    .sort((semesterA, semesterB) => {
                        // sort last if the semester is unset
                        if (semesterA === '') {
                            return 1;
                        }
                        if (semesterB === '') {
                            return -1;
                        }
                        // parse years in base 10 by extracting the two digits after the WS or SS prefix
                        const yearsCompared = parseInt(semesterB.substr(2, 2), 10) - parseInt(semesterA.substr(2, 2), 10);
                        if (yearsCompared !== 0) {
                            return yearsCompared;
                        }
                        // if years are the same, sort WS over SS
                        return semesterA.substr(0, 2) === 'WS' ? -1 : 1;
                    });

                this.semesterCollapsed = {};
                this.coursesBySemester = {};
                let firstUncollapsed = false;
                for (const semester of this.courseSemesters) {
                    this.semesterCollapsed[semester] = firstUncollapsed;
                    firstUncollapsed = true;
                    this.coursesBySemester[semester] = this.courses.filter((course) => !course.testCourse && (course.semester ?? '') === semester);
                }
                // Add an extra category for test courses
                const testCourses = this.courses.filter((course) => course.testCourse);
                if (testCourses.length > 0) {
                    this.courseSemesters[this.courseSemesters.length] = 'test';
                    this.semesterCollapsed['test'] = false;
                    this.coursesBySemester['test'] = testCourses;
                }

                // First fetch important data like title for each course
                this.courseManagementService.getExercisesForManagementOverview(this.showOnlyActive).subscribe(
                    (result: HttpResponse<CourseManagementOverviewDto[]>) => {
                        result.body!.forEach((dto) => (this.details[dto.courseId] = dto));
                    },
                    (result: HttpErrorResponse) => onError(this.jhiAlertService, result, false),
                );
                // Once the important part is loaded we can fetch the statistics
                this.courseManagementService.getStatsForManagementOverview(this.showOnlyActive).subscribe(
                    (result: HttpResponse<CourseManagementOverviewStatisticsDto[]>) => {
                        result.body!.forEach((dto) => (this.statistics[dto.courseId] = dto));
                    },
                    (result: HttpErrorResponse) => onError(this.jhiAlertService, result, false),
                );
                // load courses after initialization for guidedTour, notifications and group numbers
                this.courseManagementService.getWithUserStats({ onlyActive: this.showOnlyActive }).subscribe(
                    (result: HttpResponse<Course[]>) => {
                        this.courseForGuidedTour = this.guidedTourService.enableTourForCourseOverview(result.body!, tutorAssessmentTour, true);

                        // We use this extra map of courses to improve performance by allowing us to use OnPush change detection
                        result.body!.forEach((course) => (this.coursesWithUsers[course.id!] = course));
                    },
                    (result: HttpErrorResponse) => onError(this.jhiAlertService, result, false),
                );
            },
            (res: HttpErrorResponse) => onError(this.jhiAlertService, res, false),
        );
    }

    /**
     * loads all courses and subscribes to courseListModification
     */
    ngOnInit() {
        this.loadAll();
        this.registerChangeInCourses();
    }

    /**
     * notifies the guided-tour service that the current component has
     * been fully loaded
     */
    ngAfterViewInit(): void {
        this.guidedTourService.componentPageLoaded();
    }

    /**
     * unsubscribe on component destruction
     */
    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
        this.dialogErrorSource.unsubscribe();
    }

    /**
     * subscribes to courseListModification event
     */
    registerChangeInCourses() {
        this.eventSubscriber = this.eventManager.subscribe('courseListModification', () => this.loadAll());
    }

    /**
     * toggles the attribute showOnlyActive and reloads all courses
     */
    toggleShowOnlyActive() {
        this.showOnlyActive = !this.showOnlyActive;
        this.loadAll();
    }
}
