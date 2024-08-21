import { Component, OnDestroy, OnInit } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from '../../course/manage/course-management.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { Subscription } from 'rxjs';
import { faAngleDown, faAngleUp } from '@fortawesome/free-solid-svg-icons';
import { sortCourses } from 'app/shared/util/course.util';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';

@Component({
    standalone: true,
    selector: 'course-archive',
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule],
    templateUrl: './course-archive.component.html',
    styleUrls: ['./course-archive.component.scss'],
})
export class CourseArchiveComponent implements OnInit, OnDestroy {
    private archiveCourseSubscription: Subscription;

    courses: Course[];
    semesters: string[];
    semesterCollapsed: { [key: string]: boolean };
    coursesBySemester: { [key: string]: Course[] };
    courseColor: string;

    readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;

    //Icons
    readonly faAngleDown = faAngleDown;
    readonly faAngleUp = faAngleUp;

    constructor(
        private courseService: CourseManagementService,
        private alertService: AlertService,
    ) {}

    ngOnInit(): void {
        this.loadArchivedCourses();
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

        for (const semester of this.semesters) {
            this.semesterCollapsed[semester] = true;
            this.coursesBySemester[semester] = this.courses.filter((course) => course.semester === semester);
        }
    }

    ngOnDestroy(): void {
        this.archiveCourseSubscription.unsubscribe();
    }
}
