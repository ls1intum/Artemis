import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/foundation/service/alert.service';
import { onError } from 'app/foundation/util/global.utils';
import { Subscription } from 'rxjs';
import { faAngleDown, faAngleUp, faArrowDown19, faArrowUp19, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { SizeProp } from '@fortawesome/fontawesome-svg-core';
import { CourseCardHeaderComponent } from '../course-card-header/course-card-header.component';
import { CourseForArchiveDTO } from 'app/course/shared/entities/course-for-archive-dto';
import { SearchFilterComponent } from 'app/shared-ui/search-filter/search-filter.component';
import { SearchFilterPipe } from 'app/foundation/pipes/search-filter.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { CommonModule } from '@angular/common';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { addPublicFilePrefix } from 'app/app.constants';
import { cloneWith, deepClone } from 'app/foundation/util/deep-clone.util';

@Component({
    selector: 'jhi-course-archive',
    templateUrl: './course-archive.component.html',
    styleUrls: ['./course-archive.component.scss'],
    imports: [CourseCardHeaderComponent, SearchFilterComponent, SearchFilterPipe, TranslateDirective, ArtemisTranslatePipe, CommonModule, FontAwesomeModule, NgbTooltipModule],
})
export class CourseArchiveComponent implements OnInit, OnDestroy {
    private static readonly TEST_COURSES_GROUP_ID = 'testCourses';

    private archiveCourseSubscription?: Subscription;
    private courseService = inject(CourseManagementService);
    private alertService = inject(AlertService);

    readonly courses = signal<CourseForArchiveDTO[]>([]);
    readonly testCourses = signal<CourseForArchiveDTO[]>([]);
    readonly semesters = signal<string[]>([]);
    readonly archiveGroups = computed(() => (this.testCourses().length ? [...this.semesters(), CourseArchiveComponent.TEST_COURSES_GROUP_ID] : this.semesters()));
    readonly fullFormOfSemesterStrings = signal<{ [key: string]: string }>({});
    readonly semesterCollapsed = signal<{ [key: string]: boolean }>({});
    readonly coursesBySemester = signal<{ [key: string]: CourseForArchiveDTO[] }>({});
    readonly searchCourseText = signal('');
    readonly isSortAscending = signal(true);
    iconSize: SizeProp = 'lg';

    //Icons
    readonly faAngleDown = faAngleDown;
    readonly faAngleUp = faAngleUp;
    readonly faArrowDown19 = faArrowDown19;
    readonly faArrowUp19 = faArrowUp19;
    readonly faQuestionCircle = faQuestionCircle;

    ngOnInit(): void {
        this.loadArchivedCourses();
    }

    /**
     * Loads all courses that the student has been enrolled in from previous semesters
     */
    loadArchivedCourses(): void {
        this.archiveCourseSubscription = this.courseService.getCoursesForArchive().subscribe({
            next: (res: HttpResponse<CourseForArchiveDTO[]>) => {
                if (res.body) {
                    const courses = res.body;
                    courses.forEach((courseDto: CourseForArchiveDTO) => {
                        courseDto.icon = addPublicFilePrefix(courseDto.icon) || courseDto.icon;
                    });
                    this.courses.set(this.sortCoursesByTitle(courses.filter((course) => !course.testCourse)));
                    this.testCourses.set(this.sortCoursesByTitle(courses.filter((course) => course.testCourse)));
                    this.semesters.set(this.getUniqueSemesterNamesSorted(this.courses()));
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
        const semesterCollapsed: { [key: string]: boolean } = {};
        const coursesBySemester: { [key: string]: CourseForArchiveDTO[] } = {};
        const fullFormOfSemesterStrings: { [key: string]: string } = {};
        this.archiveGroups().forEach((group) => {
            const stored = this.courseService.getSemesterCollapseStateFromStorage(group);
            semesterCollapsed[group] = stored ?? false;
            this.courseService.setSemesterCollapseState(group, false);
            if (group === CourseArchiveComponent.TEST_COURSES_GROUP_ID) {
                coursesBySemester[group] = this.testCourses();
                fullFormOfSemesterStrings[group] = 'artemisApp.studentDashboard.archive.testCourses';
            } else if (group === '') {
                coursesBySemester[group] = this.courses().filter((course) => (course.semester ?? '') === group);
                fullFormOfSemesterStrings[group] = 'artemisApp.course.archive.semesterIndependent';
            } else {
                coursesBySemester[group] = this.courses().filter((course) => course.semester === group);
                fullFormOfSemesterStrings[group] = group.startsWith('WS') ? 'artemisApp.course.archive.winterSemester' : 'artemisApp.course.archive.summerSemester';
            }
        });
        this.semesterCollapsed.set(semesterCollapsed);
        this.coursesBySemester.set(coursesBySemester);
        this.fullFormOfSemesterStrings.set(fullFormOfSemesterStrings);
    }

    ngOnDestroy(): void {
        this.archiveCourseSubscription?.unsubscribe();
    }

    setSearchValue(searchValue: string): void {
        this.searchCourseText.set(searchValue);
        if (searchValue !== '') {
            this.expandOrCollapseBasedOnSearchValue();
        } else {
            this.getCollapseStateForSemesters();
        }
    }

    onSort(): void {
        if (this.semesters().length) {
            const semesterIndependent = this.semesters().filter((semester) => semester === '');
            const semesters = this.semesters()
                .filter((semester) => semester !== '')
                .reverse();
            this.semesters.set([...semesters, ...semesterIndependent]);
            this.isSortAscending.update((value) => !value);
        }
    }
    /**
     * if the searched text is matched with a course title, expand the accordion, otherwise collapse
     */
    expandOrCollapseBasedOnSearchValue(): void {
        const semesterCollapsed = deepClone(this.semesterCollapsed());
        for (const group of this.archiveGroups()) {
            semesterCollapsed[group] = !this.isCourseFoundInSemester(group);
        }
        this.semesterCollapsed.set(semesterCollapsed);
    }

    getCollapseStateForSemesters(): void {
        const semesterCollapsed = deepClone(this.semesterCollapsed());
        for (const group of this.archiveGroups()) {
            semesterCollapsed[group] = this.courseService.getSemesterCollapseStateFromStorage(group);
        }
        this.semesterCollapsed.set(semesterCollapsed);
    }

    toggleCollapseState(semester: string): void {
        const newState = !this.semesterCollapsed()[semester];
        this.semesterCollapsed.set(cloneWith(this.semesterCollapsed(), { [semester]: newState }));
        this.courseService.setSemesterCollapseState(semester, newState);
    }

    isCourseFoundInSemester(semester: string): boolean {
        return this.coursesBySemester()[semester].some((course) => course.title?.toLowerCase().includes(this.searchCourseText().toLowerCase()));
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
