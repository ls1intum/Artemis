import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from '../../course/manage/course-management.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { Subscription } from 'rxjs';
import { faAngleDown, faAngleUp, faArrowDown19, faArrowUp19, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { SizeProp } from '@fortawesome/fontawesome-svg-core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { CourseCardHeaderComponent } from '../course-card-header/course-card-header.component';
import { CourseForArchiveDTO } from 'app/course/manage/course-for-archive-dto';

@Component({
    selector: 'jhi-course-archive',
    templateUrl: './course-archive.component.html',
    styleUrls: ['./course-archive.component.scss'],
    standalone: true,
    imports: [ArtemisSharedModule, CourseCardHeaderComponent],
})
export class CourseArchiveComponent implements OnInit, OnDestroy {
    private archiveCourseSubscription: Subscription;
    private courseService = inject(CourseManagementService);
    private alertService = inject(AlertService);

    courses: CourseForArchiveDTO[] = [];
    semesters: string[];
    fullFormOfSemesterStrings: { [key: string]: string } = {};
    semesterCollapsed: { [key: string]: boolean } = {};
    coursesBySemester: { [key: string]: Course[] } = {};
    searchCourseText = '';
    isSortAscending = true;
    iconSize: SizeProp = 'lg';

    //Icons
    readonly faAngleDown = faAngleDown;
    readonly faAngleUp = faAngleUp;
    readonly faArrowDown19 = faArrowDown19;
    readonly faArrowUp19 = faArrowUp19;
    readonly faQuestionCircle = faQuestionCircle;

    ngOnInit(): void {
        this.loadArchivedCourses();
        this.courseService.enableCourseOverviewBackground();
    }

    /**
     * Loads all courses that the student has been enrolled in from previous semesters
     */
    loadArchivedCourses(): void {
        this.archiveCourseSubscription = this.courseService.getCoursesForArchive().subscribe({
            next: (res: HttpResponse<CourseForArchiveDTO[]>) => {
                if (res.body) {
                    this.courses = res.body || [];
                    this.courses = this.sortCoursesByTitle(this.courses);
                    this.semesters = this.getUniqueSemesterNamesSorted(this.courses);
                    this.mapCoursesIntoSemesters();
                }
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }

    /**
     * maps existing courses to each semester
     */
    mapCoursesIntoSemesters(): void {
        this.semesters.forEach((semester) => {
            this.semesterCollapsed[semester] = false;
            this.courseService.setSemesterCollapseState(semester, false);
            this.coursesBySemester[semester] = this.courses.filter((course) => course.semester === semester);
            this.fullFormOfSemesterStrings[semester] = semester.startsWith('WS') ? 'artemisApp.course.archive.winterSemester' : 'artemisApp.course.archive.summerSemester';
        });
    }

    ngOnDestroy(): void {
        this.archiveCourseSubscription.unsubscribe();
        this.courseService.disableCourseOverviewBackground();
    }

    setSearchValue(searchValue: string): void {
        this.searchCourseText = searchValue;
        if (searchValue !== '') {
            this.expandOrCollapseBasedOnSearchValue();
        } else {
            this.getCollapseStateForSemesters();
        }
    }

    onSort(): void {
        if (this.semesters) {
            this.semesters.reverse();
            this.isSortAscending = !this.isSortAscending;
        }
    }
    /**
     * if the searched text is matched with a course title, expand the accordion, otherwise collapse
     */
    expandOrCollapseBasedOnSearchValue(): void {
        for (const semester of this.semesters) {
            const hasMatchingCourse = this.coursesBySemester[semester].some((course) => course.title?.toLowerCase().includes(this.searchCourseText.toLowerCase()));
            this.semesterCollapsed[semester] = !hasMatchingCourse;
        }
    }

    getCollapseStateForSemesters(): void {
        for (const semester of this.semesters) {
            this.semesterCollapsed[semester] = this.courseService.getSemesterCollapseStateFromStorage(semester);
        }
    }

    toggleCollapseState(semester: string): void {
        this.semesterCollapsed[semester] = !this.semesterCollapsed[semester];
        this.courseService.setSemesterCollapseState(semester, this.semesterCollapsed[semester]);
    }

    isCourseFoundInSemester(semester: string): boolean {
        return this.coursesBySemester[semester].some((course) => course.title?.toLowerCase().includes(this.searchCourseText.toLowerCase()));
    }

    sortCoursesByTitle(courses: CourseForArchiveDTO[]): CourseForArchiveDTO[] {
        return courses.sort((courseA, courseB) => (courseA.title ?? '').localeCompare(courseB.title ?? ''));
    }

    getUniqueSemesterNamesSorted(courses: CourseForArchiveDTO[]): string[] {
        return (
            courses
                .map((course) => course.semester ?? '')
                // filter down to unique values
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
                    const prefixA = semesterA.slice(0, 2);
                    const prefixB = semesterB.slice(0, 2);

                    if (prefixA === prefixB) {
                        return 0; // Both semesters are the same (either both WS or both SS)
                    }

                    return prefixA === 'WS' ? -1 : 1; // WS should be placed above SS
                })
        );
    }
}
