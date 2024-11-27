import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router } from '@angular/router';
import { Observable, of, switchMap } from 'rxjs';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import dayjs from 'dayjs/esm';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { CourseOverviewRoutePath } from 'app/overview/courses-routing.module';

@Injectable({
    providedIn: 'root',
})
export class CourseOverviewGuard implements CanActivate {
    private courseStorageService = inject(CourseStorageService);
    private courseManagementService = inject(CourseManagementService);
    private router = inject(Router);
    private serverDateService = inject(ArtemisServerDateService);

    /**
     * Check if the client can activate a course overview route.
     * @return true if the client is allowed to access the route, false otherwise
     */
    canActivate(route: ActivatedRouteSnapshot): Observable<boolean> {
        const courseIdString = route.parent?.paramMap.get('courseId');
        if (!courseIdString) {
            return of(false);
        }
        const courseIdNumber = parseInt(courseIdString, 10);

        const path = route.routeConfig?.path;

        const course = this.courseStorageService.getCourse(courseIdNumber);
        if (course) {
            return this.handleReturn(course, path);
        }

        return this.courseManagementService.find(courseIdNumber).pipe(
            switchMap((res) => {
                if (res.body) {
                    // Store course in cache
                    this.courseStorageService.updateCourse(res.body);
                }
                // Flatten the result to return Observable<boolean> directly
                return this.handleReturn(this.courseStorageService.getCourse(courseIdNumber), path);
            }),
        );
    }

    handleReturn = (course?: Course, type?: string): Observable<boolean> => {
        let hasAccess: boolean;
        switch (type) {
            case CourseOverviewRoutePath.EXERCISES:
                hasAccess = true; //default route, should always be accessible
                break;
            case CourseOverviewRoutePath.LECTURES:
                hasAccess = !!course?.lectures;
                break;
            case CourseOverviewRoutePath.EXAMS:
                hasAccess = this.hasVisibleExams(course);
                break;
            case CourseOverviewRoutePath.COMPETENCIES:
                hasAccess = !!(course?.numberOfCompetencies || course?.numberOfPrerequisites);
                break;
            case CourseOverviewRoutePath.TUTORIAL_GROUPS:
                hasAccess = !!course?.numberOfTutorialGroups;
                break;
            case CourseOverviewRoutePath.DASHBOARD:
                hasAccess = course?.studentCourseAnalyticsDashboardEnabled ?? false;
                break;
            case CourseOverviewRoutePath.FAQ:
                hasAccess = course?.faqEnabled ?? false;
                break;
            case CourseOverviewRoutePath.LEARNING_PATH:
                hasAccess = course?.learningPathsEnabled ?? false;
                break;
            default:
                hasAccess = false;
        }
        if (!hasAccess) {
            this.router.navigate([`/courses/${course?.id}/exercises`]);
        }
        return of(hasAccess);
    };

    hasVisibleExams(course?: Course): boolean {
        if (course?.exams) {
            for (const exam of course.exams) {
                if (exam.visibleDate && dayjs(exam.visibleDate).isBefore(this.serverDateService.now())) {
                    return true;
                }
            }
        }
        return false;
    }
}
