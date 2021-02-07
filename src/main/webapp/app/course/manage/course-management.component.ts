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
import { CourseManagementOverviewDetailsDto } from './overview/course-management-overview-details-dto.model';

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

    courses: CourseManagementOverviewDetailsDto[];
    details = new Map<number, CourseManagementOverviewDto>();
    statistics = new Map<number, CourseManagementOverviewStatisticsDto>();
    courseSemesters: string[];
    semesterCollapsed: { [key: string]: boolean };
    coursesBySemester: { [key: string]: CourseManagementOverviewDetailsDto[] };
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
            (res: HttpResponse<CourseManagementOverviewDetailsDto[]>) => {
                this.courses = res.body!;
                this.courseSemesters = this.courses
                    // test courses get their own section later
                    .filter((c) => !c.testCourse)
                    .map((c) => c.semester ?? '')
                    // filter down to unique values
                    .filter((course, index, courses) => courses.indexOf(course) === index)
                    .sort((a, b) => {
                        // sort last if the semester is unset
                        if (a === '') {
                            return 1;
                        }
                        if (b === '') {
                            return -1;
                        }
                        // parse years in base 10 by extracting the two digits after the WS or SS prefix
                        const yearsCompared = parseInt(b.substr(2, 2), 10) - parseInt(a.substr(2, 2), 10);
                        if (yearsCompared !== 0) {
                            return yearsCompared;
                        }
                        // if years are the same, sort WS over SS
                        return a.substr(0, 2) === 'WS' ? -1 : 1;
                    });

                this.semesterCollapsed = {};
                this.coursesBySemester = {};
                let firstUncollapsed = false;
                for (const semester of this.courseSemesters) {
                    this.semesterCollapsed[semester] = firstUncollapsed;
                    firstUncollapsed = true;
                    this.coursesBySemester[semester] = this.courses.filter((c) => !c.testCourse && (c.semester ?? '') === semester);
                }
                // Add an extra category for test courses
                if (this.courses.find((c) => c.testCourse) !== null) {
                    this.courseSemesters[this.courseSemesters.length] = 'test';
                    this.semesterCollapsed['test'] = false;
                    this.coursesBySemester['test'] = this.courses.filter((c) => c.testCourse);
                }

                // First fetch important data like title for each course
                this.courseManagementService.getExercisesForManagementOverview(this.courses.map((c) => c.id!)).subscribe(
                    (result: HttpResponse<CourseManagementOverviewDto[]>) => {
                        result.body!.forEach((dto) => {
                            this.details[dto.courseId] = dto;
                            if (!this.statistics[dto.courseId]) {
                                this.statistics[dto.courseId] = new CourseManagementOverviewStatisticsDto();
                            }
                        });
                    },
                    (result: HttpErrorResponse) => onError(this.jhiAlertService, result, false),
                );
                // Once the important part is loaded we can fetch the statistics
                this.courseManagementService.getStatsForManagementOverview(this.courses.map((c) => c.id!)).subscribe(
                    (result: HttpResponse<CourseManagementOverviewStatisticsDto[]>) => {
                        result.body!.forEach((dto) => (this.statistics[dto.courseId] = dto));
                    },
                    (result: HttpErrorResponse) => onError(this.jhiAlertService, result, false),
                );
                // load courses after initialization for guidedTour, notifications and group numbers
                this.courseManagementService.getWithUserStats({ onlyActive: this.showOnlyActive }).subscribe(
                    (result: HttpResponse<Course[]>) => {
                        this.courseForGuidedTour = this.guidedTourService.enableTourForCourseOverview(result.body!, tutorAssessmentTour, true);
                        for (const course of this.courses) {
                            const courseWithUsers = result.body!.find((c) => c.id === course.id);
                            if (courseWithUsers) {
                                course.numberOfStudents = courseWithUsers.numberOfStudents;
                                course.numberOfTeachingAssistants = courseWithUsers.numberOfTeachingAssistants;
                                course.numberOfInstructors = courseWithUsers.numberOfInstructors;
                            }
                        }
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

    /**
     * toggles the attribute showOnlyActive and reloads all courses
     */
    toggleShowOnlyActive() {
        this.showOnlyActive = !this.showOnlyActive;
        this.loadAll();
    }
}
