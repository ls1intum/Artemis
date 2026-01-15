import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subject, Subscription } from 'rxjs';
import { Course } from 'app/core/course/shared/entities/course.model';
import { CourseManagementService } from '../services/course-management.service';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/shared/service/alert.service';
import { CourseManagementOverviewStatisticsDto } from 'app/core/course/manage/overview/course-management-overview-statistics-dto.model';
import { EventManager } from 'app/shared/service/event-manager.service';
import { faAngleDown, faAngleUp, faBook, faPlus } from '@fortawesome/free-solid-svg-icons';
import { DocumentationType } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DocumentationButtonComponent } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { CourseManagementCardComponent } from '../overview/course-management-card.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CourseAccessStorageService } from 'app/core/course/shared/services/course-access-storage.service';
import { addPublicFilePrefix } from 'app/app.constants';
import { AccountService } from 'app/core/auth/account.service';

@Component({
    selector: 'jhi-course',
    templateUrl: './course-management.component.html',
    styles: ['.course-table {padding-bottom: 5rem}'],
    styleUrls: ['./course-management.component.scss'],
    imports: [TranslateDirective, DocumentationButtonComponent, RouterLink, FaIconComponent, CourseManagementCardComponent, ArtemisTranslatePipe],
})
export class CourseManagementComponent implements OnInit, OnDestroy {
    private courseManagementService = inject(CourseManagementService);
    private alertService = inject(AlertService);
    private eventManager = inject(EventManager);
    private courseAccessStorageService = inject(CourseAccessStorageService);
    private accountService = inject(AccountService);

    readonly showOnlyActive = signal(true);

    readonly courses = signal<Course[] | undefined>(undefined);
    readonly statistics = signal(new Map<number, CourseManagementOverviewStatisticsDto>());
    readonly coursesWithExercises = signal(new Map<number, Course>());
    readonly coursesWithUsers = signal(new Map<number, Course>());
    readonly courseSemesters = signal<string[]>([]);
    readonly semesterCollapsed = signal<{ [key: string]: boolean }>({});
    readonly coursesBySemester = signal<{ [key: string]: Course[] }>({});

    private eventSubscriber: Subscription;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    readonly documentationType: DocumentationType = 'Course';
    // Icons
    readonly faPlus = faPlus;
    readonly faAngleDown = faAngleDown;
    readonly faAngleUp = faAngleUp;
    readonly faBook = faBook;
    protected readonly isAdmin = computed(() => this.accountService.isAdmin());
    protected readonly isAuthenticated = this.accountService.authenticated;

    /**
     * loads all courses and subscribes to courseListModification
     */
    ngOnInit() {
        this.loadAll();
        this.registerChangeInCourses();
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
        this.courseManagementService.getCourseOverview({ onlyActive: this.showOnlyActive() }).subscribe({
            next: (res: HttpResponse<Course[]>) => {
                const sortedCourses = res.body!.sort((a, b) => (a.title ?? '').localeCompare(b.title ?? ''));
                this.courses.set(sortedCourses);

                this.courseSemesters.set(this.getUniqueSemesterNamesSorted(sortedCourses));
                this.sortCoursesIntoSemesters();

                // Set the course icons for each course
                this.setCourseIcons();

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
     * Sets the public course icon path for the specific course (by prepending the REST-path prefix).
     */
    private setCourseIcons(): void {
        const currentCourses = this.courses();
        if (currentCourses) {
            currentCourses.forEach((course) => {
                course.courseIconPath = addPublicFilePrefix(course.courseIcon);
            });
        }
    }

    /**
     * Sorts the courses into the coursesBySemester map.
     * Fills the semesterCollapsed map depending on if the semester should be expanded by default.
     * The first semester group, the test courses and the recently accessed courses are expanded by default.
     */
    private sortCoursesIntoSemesters(): void {
        const newSemesterCollapsed: { [key: string]: boolean } = {};
        const newCoursesBySemester: { [key: string]: Course[] } = {};
        const currentCourses = this.courses() ?? [];
        const currentSemesters = this.courseSemesters();

        // Get last accessed courses
        const lastAccessedCourseIds = this.courseAccessStorageService.getLastAccessedCourses(CourseAccessStorageService.STORAGE_KEY);
        const recentlyAccessedCourses = currentCourses.filter((course) => lastAccessedCourseIds.includes(course.id!));

        let firstExpanded = false;
        const updatedSemesters = [...currentSemesters];
        for (const semester of currentSemesters) {
            newSemesterCollapsed[semester] = firstExpanded;
            firstExpanded = true;
            newCoursesBySemester[semester] = currentCourses.filter(
                (course) => !course.testCourse && !lastAccessedCourseIds.includes(course.id!) && (course.semester ?? '') === semester,
            );
        }

        // Add a new category "recent"
        updatedSemesters.unshift('recent');
        newSemesterCollapsed['recent'] = false;
        newCoursesBySemester['recent'] = recentlyAccessedCourses;

        // Add an extra category for test courses
        const testCourses = currentCourses.filter((course) => course.testCourse && !lastAccessedCourseIds.includes(course.id!));
        if (testCourses.length > 0) {
            updatedSemesters.push('test');
            newSemesterCollapsed['test'] = false;
            newCoursesBySemester['test'] = testCourses;
        }

        // Remove all semesters that have no courses
        const filteredSemesters = updatedSemesters.filter((semester) => newCoursesBySemester[semester].length > 0);

        this.courseSemesters.set(filteredSemesters);
        this.semesterCollapsed.set(newSemesterCollapsed);
        this.coursesBySemester.set(newCoursesBySemester);
    }

    /**
     * Gets the exercises to display from the server and sorts them into coursesWithExercises by course id
     */
    private fetchExercises(): void {
        this.courseManagementService.getExercisesForManagementOverview(this.showOnlyActive()).subscribe({
            next: (result: HttpResponse<Course[]>) => {
                // We use this extra map of courses to improve performance by allowing us to use OnPush change detection
                const newCoursesWithExercises = new Map<number, Course>();
                result.body!.forEach((course) => {
                    if (course.id) {
                        newCoursesWithExercises.set(course.id, course);
                    }
                });
                this.coursesWithExercises.set(newCoursesWithExercises);
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }

    /**
     * Gets the exercise statistics to display from the server and sorts them into the statistics map by course id
     */
    private fetchExerciseStats(): void {
        this.courseManagementService.getStatsForManagementOverview(this.showOnlyActive()).subscribe({
            next: (result: HttpResponse<CourseManagementOverviewStatisticsDto[]>) => {
                const newStatistics = new Map<number, CourseManagementOverviewStatisticsDto>();
                result.body!.forEach((statisticsDTO) => {
                    if (statisticsDTO.courseId) {
                        newStatistics.set(statisticsDTO.courseId, statisticsDTO);
                    }
                });
                this.statistics.set(newStatistics);
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }

    /**
     * Gets the amount of users in the user groups to display and sorts them into coursesWithUsers by course id
     */
    private fetchUserStats(): void {
        this.courseManagementService.getWithUserStats({ onlyActive: this.showOnlyActive() }).subscribe({
            next: (result: HttpResponse<Course[]>) => {
                // We use this extra map of courses to improve performance by allowing us to use OnPush change detection
                const newCoursesWithUsers = new Map<number, Course>();
                result.body!.forEach((course) => {
                    if (course.id) {
                        newCoursesWithUsers.set(course.id, course);
                    }
                });
                this.coursesWithUsers.set(newCoursesWithUsers);
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
        this.showOnlyActive.update((value) => !value);
        this.loadAll();
    }

    /**
     * Toggles the collapsed state of a semester section
     */
    toggleSemesterCollapsed(semester: string) {
        this.semesterCollapsed.update((collapsed) => ({
            ...collapsed,
            [semester]: !collapsed[semester],
        }));
    }
}
