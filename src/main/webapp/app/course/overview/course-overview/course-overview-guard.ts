import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router } from '@angular/router';
import { Observable, catchError, of, switchMap } from 'rxjs';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { Course, isCommunicationEnabled } from 'app/course/shared/entities/course.model';
import dayjs from 'dayjs/esm';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import { CourseOverviewRoutePath } from 'app/course/overview/courses.route';

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
        if (!path) {
            return of(false);
        }
        const course = this.courseStorageService.getCourse(courseIdNumber);
        if (course && this.courseStorageService.isCourseFullyLoaded(courseIdNumber)) {
            // Fast path: the full course is already loaded (e.g. when switching between tabs), so decide without a request.
            return this.handleReturn(course, path);
        }
        // First navigation into the course: only the slim course from the course list might be stored (it e.g. always
        // has empty exams and would produce wrong access decisions). Load the full course once and decide BEFORE
        // activating the route, so an inaccessible tab never briefly mounts. findOneForDashboard stores the result, so
        // the course container reuses it instead of fetching again. On a load error (e.g. 403 for an unregistered user)
        // allow activation; the container's loadCourse then handles it (course registration redirect / alert).
        return this.courseManagementService.findOneForDashboard(courseIdNumber).pipe(
            switchMap((courseRes) => this.handleReturn(courseRes.body ?? undefined, path)),
            catchError(() => of(true)),
        );
    }

    handleReturn = (course?: Course, type?: string): Observable<boolean> => {
        let hasAccess: boolean;
        switch (type) {
            // Should always be accessible
            case CourseOverviewRoutePath.EXERCISES:
                hasAccess = true;
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
            case CourseOverviewRoutePath.IRIS:
                hasAccess = course?.irisEnabledInCourse ?? false;
                break;
            case CourseOverviewRoutePath.FAQ:
                hasAccess = (course?.numberOfAcceptedFaqs ?? 0) > 0;
                break;
            case CourseOverviewRoutePath.LEARNING_PATH:
                hasAccess = course?.learningPathsEnabled ?? false;
                break;
            case CourseOverviewRoutePath.COMMUNICATION:
                hasAccess = isCommunicationEnabled(course);
                break;
            case CourseOverviewRoutePath.TRAINING:
            case CourseOverviewRoutePath.TRAINING_QUIZ:
                hasAccess = course?.trainingEnabled ?? false;
                break;
            default:
                hasAccess = false;
        }
        if (!hasAccess) {
            void this.router.navigate([`/courses/${course?.id}/exercises`]);
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
