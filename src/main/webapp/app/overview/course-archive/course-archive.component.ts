import { Component, OnDestroy, OnInit } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from '../../course/manage/course-management.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { Subscription } from 'rxjs';
import { faAngleDown, faAngleUp, faArrowDownAZ, faArrowUpAZ, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { sortCourses } from 'app/shared/util/course.util';
import { SizeProp } from '@fortawesome/fontawesome-svg-core';

@Component({
    selector: 'course-archive',
    templateUrl: './course-archive.component.html',
    styleUrls: ['./course-archive.component.scss'],
})
export class CourseArchiveComponent implements OnInit, OnDestroy {
    private archiveCourseSubscription: Subscription;

    courses: Course[];
    semesters: string[];
    expandedSemesterStrings: { [key: string]: string };
    semesterCollapsed: { [key: string]: boolean };
    coursesBySemester: { [key: string]: Course[] };
    searchCourseText = '';
    isSortAscending = true;
    iconSize: SizeProp = 'lg';

    //Icons
    readonly faAngleDown = faAngleDown;
    readonly faAngleUp = faAngleUp;
    readonly faArrowDownAZ = faArrowDownAZ;
    readonly faArrowUpAZ = faArrowUpAZ;
    readonly faQuestionCircle = faQuestionCircle;

    constructor(
        private courseService: CourseManagementService,
        private alertService: AlertService,
    ) {}

    ngOnInit(): void {
        this.loadArchivedCourses();
        this.courseService.enableCourseOverviewBackground();
    }

    /**
     * Loads all courses that the student has been enrolled in
     */
    loadArchivedCourses(): void {
        this.archiveCourseSubscription = this.courseService.getCoursesForArchive().subscribe({
            next: (response: HttpResponse<Course[]>) => {
                this.courses = response.body || [];
                this.courses = sortCourses(this.courses);
                this.semesters = this.courseService.getUniqueSemesterNamesSorted(this.courses);
                this.mapCoursesIntoSemesters();
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }

    private mapCoursesIntoSemesters(): void {
        this.semesterCollapsed = {};
        this.coursesBySemester = {};

        let isCollapsed = false;
        for (const semester of this.semesters) {
            this.semesterCollapsed[semester] = isCollapsed;
            this.coursesBySemester[semester] = this.courses.filter((course) => course.semester === semester);
            isCollapsed = true;
        }
    }

    ngOnDestroy(): void {
        this.archiveCourseSubscription.unsubscribe();
        this.courseService.disableCourseOverviewBackground();
    }

    setSearchValue(searchValue: string): void {
        this.searchCourseText = searchValue;
        if (searchValue !== '') {
            this.expandOrCollapseBasedOnSearchValue();
        }
    }

    onSort(): void {
        if (this.semesters) {
            this.semesters.reverse();
            this.isSortAscending = !this.isSortAscending;
        }
    }

    expandOrCollapseBasedOnSearchValue(): void {
        for (const semester of this.semesters) {
            const hasMatchingCourse = this.coursesBySemester[semester].some((course) => course.title?.toLowerCase().includes(this.searchCourseText.toLowerCase()));
            this.semesterCollapsed[semester] = !hasMatchingCourse;
        }
    }

    mapSemesterString(semester: string): string {
        const year = semester.slice(2);
        if (semester.startsWith('WS')) {
            return `Wintersemester 20${year}`;
        } else {
            return `Sommersemester 20${year}`;
        }
    }
}
