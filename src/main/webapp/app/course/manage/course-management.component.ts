import { AfterViewInit, Component, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subject, Subscription } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from './course-management.service';
import { onError } from 'app/shared/util/global.utils';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { tutorAssessmentTour } from 'app/guided-tour/tours/tutor-assessment-tour';
import { AlertService } from 'app/core/util/alert.service';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { LectureService } from 'app/lecture/lecture.service';
import { CourseManagementOverviewStatisticsDto } from 'app/course/manage/overview/course-management-overview-statistics-dto.model';
import { EventManager } from 'app/core/util/event-manager.service';
import { faAngleDown, faAngleUp, faPlus } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-course',
    templateUrl: './course-management.component.html',
    styles: ['.course-table {padding-bottom: 5rem}'],
    styleUrls: ['./course-management.component.scss'],
})
export class CourseManagementComponent implements OnInit, OnDestroy, AfterViewInit {
    showOnlyActive = true;

    courses: Course[];
    statistics = new Map<number, CourseManagementOverviewStatisticsDto>();
    coursesWithExercises = new Map<number, Course>();
    coursesWithUsers = new Map<number, Course>();
    courseSemesters: string[];
    semesterCollapsed: { [key: string]: boolean };
    coursesBySemester: { [key: string]: Course[] };
    eventSubscriber: Subscription;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    courseForGuidedTour?: Course;

    // Icons
    faPlus = faPlus;
    faAngleDown = faAngleDown;
    faAngleUp = faAngleUp;

    constructor(
        private examService: ExamManagementService,
        private lectureService: LectureService,
        private courseManagementService: CourseManagementService,
        private alertService: AlertService,
        private eventManager: EventManager,
        private guidedTourService: GuidedTourService,
    ) {}

    /**
     * loads all courses from courseService
     */
    loadAll() {
        this.courseManagementService.getCourseOverview({ onlyActive: this.showOnlyActive }).subscribe({
            next: (res: HttpResponse<Course[]>) => {
                this.courses = res.body!.sort((a, b) => (a.title ?? '').localeCompare(b.title ?? ''));
                this.courseForGuidedTour = this.guidedTourService.enableTourForCourseOverview(this.courses, tutorAssessmentTour, true);

                this.courseSemesters = this.getUniqueSemesterNamesSorted(this.courses);
                this.sortCoursesIntoSemesters();

                // First fetch important data like title for each exercise
                this.fetchExercises();

                // Once the important part is loaded we can fetch the statistics
                this.fetchExerciseStats();

                // Load the user group numbers lastly
                this.fetchUserStats();
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }

    /**
     * Sorts and returns the semesters by year descending
     * WS is sorted above SS
     *
     * @param coursesWithSemesters the courses to sort the semesters of
     * @return An array of sorted semester names
     */
    private getUniqueSemesterNamesSorted(coursesWithSemesters: Course[]): string[] {
        return (
            coursesWithSemesters
                // Test courses get their own section later
                .filter((course) => !course.testCourse)
                .map((course) => course.semester ?? '')
                // Filter down to unique values
                .filter((course, index, courses) => courses.indexOf(course) === index)
                .sort((semesterA, semesterB) => {
                    // Sort last if the semester is unset
                    if (semesterA === '') {
                        return 1;
                    }
                    if (semesterB === '') {
                        return -1;
                    }

                    // Parse years in base 10 by extracting the two digits after the WS or SS prefix
                    const yearsCompared = parseInt(semesterB.slice(2, 4), 10) - parseInt(semesterA.slice(2, 4), 10);
                    if (yearsCompared !== 0) {
                        return yearsCompared;
                    }

                    // If years are the same, sort WS over SS
                    return semesterA.slice(0, 2) === 'WS' ? -1 : 1;
                })
        );
    }

    /**
     * Sorts the courses into the coursesBySemester map.
     * Fills the semesterCollapsed map depending on if the semester should be uncollapsed by default.
     * The first semester is always uncollapsed. The test course group is also uncollapsed.
     */
    private sortCoursesIntoSemesters(): void {
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
    }

    /**
     * Gets the exercises to display from the server and sorts them into coursesWithExercises by course id
     */
    private fetchExercises(): void {
        this.courseManagementService.getExercisesForManagementOverview(this.showOnlyActive).subscribe({
            next: (result: HttpResponse<Course[]>) => {
                // We use this extra map of courses to improve performance by allowing us to use OnPush change detection
                result.body!.forEach((course) => {
                    if (course.id) {
                        this.coursesWithExercises[course.id] = course;
                    }
                });
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }

    /**
     * Gets the exercise statistics to display from the server and sorts them into the statistics map by course id
     */
    private fetchExerciseStats(): void {
        this.courseManagementService.getStatsForManagementOverview(this.showOnlyActive).subscribe({
            next: (result: HttpResponse<CourseManagementOverviewStatisticsDto[]>) => {
                result.body!.forEach((statisticsDTO) => {
                    if (statisticsDTO.courseId) {
                        this.statistics[statisticsDTO.courseId] = statisticsDTO;
                    }
                });
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }

    /**
     * Gets the amount of users in the user groups to display and sorts them into coursesWithUsers by course id
     */
    private fetchUserStats(): void {
        this.courseManagementService.getWithUserStats({ onlyActive: this.showOnlyActive }).subscribe({
            next: (result: HttpResponse<Course[]>) => {
                // We use this extra map of courses to improve performance by allowing us to use OnPush change detection
                result.body!.forEach((course) => {
                    if (course.id) {
                        this.coursesWithUsers[course.id] = course;
                    }
                });
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
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
