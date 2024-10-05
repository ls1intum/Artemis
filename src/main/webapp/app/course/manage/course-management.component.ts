import { AfterViewInit, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subject, Subscription } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from './course-management.service';
import { onError } from 'app/shared/util/global.utils';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { tutorAssessmentTour } from 'app/guided-tour/tours/tutor-assessment-tour';
import { AlertService } from 'app/core/util/alert.service';
import { CourseManagementOverviewStatisticsDto } from 'app/course/manage/overview/course-management-overview-statistics-dto.model';
import { EventManager } from 'app/core/util/event-manager.service';
import { faAngleDown, faAngleUp, faPlus } from '@fortawesome/free-solid-svg-icons';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { CourseAccessStorageService } from 'app/course/course-access-storage.service';

@Component({
    selector: 'jhi-course',
    templateUrl: './course-management.component.html',
    styles: ['.course-table {padding-bottom: 5rem}'],
    styleUrls: ['./course-management.component.scss'],
})
export class CourseManagementComponent implements OnInit, OnDestroy, AfterViewInit {
    private courseManagementService = inject(CourseManagementService);
    private alertService = inject(AlertService);
    private eventManager = inject(EventManager);
    private guidedTourService = inject(GuidedTourService);
    private courseAccessStorageService = inject(CourseAccessStorageService);

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

    readonly documentationType: DocumentationType = 'Course';
    // Icons
    faPlus = faPlus;
    faAngleDown = faAngleDown;
    faAngleUp = faAngleUp;

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
     * Fills the semesterCollapsed map depending on if the semester should be expanded by default.
     * The first semester group, the test courses and the recently accessed courses are expanded by default.
     */
    private sortCoursesIntoSemesters(): void {
        this.semesterCollapsed = {};
        this.coursesBySemester = {};

        // Get last accessed courses
        const lastAccessedCourseIds = this.courseAccessStorageService.getLastAccessedCourses(CourseAccessStorageService.STORAGE_KEY);
        const recentlyAccessedCourses = this.courses.filter((course) => lastAccessedCourseIds.includes(course.id!));

        let firstExpanded = false;
        for (const semester of this.courseSemesters) {
            this.semesterCollapsed[semester] = firstExpanded;
            firstExpanded = true;
            this.coursesBySemester[semester] = this.courses.filter(
                (course) => !course.testCourse && !lastAccessedCourseIds.includes(course.id!) && (course.semester ?? '') === semester,
            );
        }

        // Add a new category "recent"
        this.courseSemesters.unshift('recent');
        this.semesterCollapsed['recent'] = false;
        this.coursesBySemester['recent'] = recentlyAccessedCourses;

        // Add an extra category for test courses
        const testCourses = this.courses.filter((course) => course.testCourse && !lastAccessedCourseIds.includes(course.id!));
        if (testCourses.length > 0) {
            this.courseSemesters[this.courseSemesters.length] = 'test';
            this.semesterCollapsed['test'] = false;
            this.coursesBySemester['test'] = testCourses;
        }

        // Remove all semesters that have no courses
        this.courseSemesters = this.courseSemesters.filter((semester) => this.coursesBySemester[semester].length > 0);
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
                        this.coursesWithExercises.set(course.id, course);
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
                        this.statistics.set(statisticsDTO.courseId, statisticsDTO);
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
                        this.coursesWithUsers.set(course.id, course);
                    }
                });
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
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
