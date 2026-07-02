import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router } from '@angular/router';
import { Observable, catchError, forkJoin, from, of, switchMap } from 'rxjs';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { Course, isCommunicationEnabled } from 'app/course/shared/entities/course.model';
import dayjs from 'dayjs/esm';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import { CourseOverviewRoutePath } from 'app/course/overview/courses.route';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/account/user/user.model';
import { LLMSelectionDecision } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';

@Injectable({
    providedIn: 'root',
})
export class CourseOverviewGuard implements CanActivate {
    private courseStorageService = inject(CourseStorageService);
    private courseManagementService = inject(CourseManagementService);
    private accountService = inject(AccountService);
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
        // Resolving the current user is only needed for the dashboard fallback; other paths don't depend on it.
        // If identity() rejects (e.g. transient network error), treat it as unknown — this falls back to today's Iris-or-exercises behavior.
        const user$: Observable<User | undefined> =
            path === CourseOverviewRoutePath.DASHBOARD ? from(this.accountService.identity()).pipe(catchError(() => of(undefined))) : of(undefined);
        const course = this.courseStorageService.getCourse(courseIdNumber);
        if (course && this.courseStorageService.isCourseFullyLoaded(courseIdNumber)) {
            // Fast path: the full course is already loaded (e.g. when switching between tabs), so decide without a request.
            return user$.pipe(switchMap((user) => this.handleReturn(course, path, user)));
        }
        // First navigation into the course: only the slim course from the course list might be stored (it e.g. always
        // has empty exams and would produce wrong access decisions). Load the full course once and decide BEFORE
        // activating the route, so an inaccessible tab never briefly mounts. findOneForDashboard stores the result, so
        // the course container reuses it instead of fetching again. On a load error (e.g. 403 for an unregistered user)
        // allow activation; the container's loadCourse then handles it (course registration redirect / alert).
        return forkJoin({
            courseRes: this.courseManagementService.findOneForDashboard(courseIdNumber),
            user: user$,
        }).pipe(
            switchMap(({ courseRes, user }) => this.handleReturn(courseRes.body ?? undefined, path, user)),
            catchError(() => of(true)),
        );
    }

    handleReturn = (course?: Course, type?: string, user?: User): Observable<boolean> => {
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
            case CourseOverviewRoutePath.DASHBOARD:
                hasAccess = !!course?.studentCourseAnalyticsDashboardEnabled;
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
            const hasOptedOutOfAI = user?.selectedLLMUsage === LLMSelectionDecision.NO_AI;
            if (type === CourseOverviewRoutePath.DASHBOARD && course?.irisEnabledInCourse && !hasOptedOutOfAI) {
                this.router.navigate([`/courses/${course?.id}/iris`]);
            } else {
                this.router.navigate([`/courses/${course?.id}/exercises`]);
            }
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
