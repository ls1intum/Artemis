import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router } from '@angular/router';
import { Observable, catchError, forkJoin, from, map, of } from 'rxjs';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { CourseTabAccess } from 'app/course/shared/entities/course-tab-access.model';
import { CourseOverviewRoutePath } from 'app/course/overview/courses.route';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/account/user/user.model';
import { LLMSelectionDecision } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';

@Injectable({
    providedIn: 'root',
})
export class CourseOverviewGuard implements CanActivate {
    private courseManagementService = inject(CourseManagementService);
    private accountService = inject(AccountService);
    private router = inject(Router);

    /**
     * Check if the client can activate a course overview route.
     *
     * The guard only loads the lightweight per-tab access flags (a handful of cheap existence/count queries) and decides
     * BEFORE the route activates, so an inaccessible tab never briefly mounts. The (expensive) course content is fetched
     * separately by the course container. On a load error (e.g. 403 for an unregistered user) activation is allowed; the
     * container's loadCourse then handles it (course registration redirect / alert).
     *
     * @return true if the client is allowed to access the route, false otherwise
     */
    canActivate(route: ActivatedRouteSnapshot): Observable<boolean> {
        const courseIdString = route.parent?.paramMap.get('courseId');
        if (!courseIdString) {
            return of(false);
        }
        const courseId = parseInt(courseIdString, 10);

        const path = route.routeConfig?.path;
        if (!path) {
            return of(false);
        }
        // Resolving the current user is only needed for the dashboard fallback; other paths don't depend on it.
        // If identity() rejects (e.g. transient network error), treat it as unknown — this falls back to the Iris-or-exercises behavior.
        const user$: Observable<User | undefined> =
            path === CourseOverviewRoutePath.DASHBOARD ? from(this.accountService.identity()).pipe(catchError(() => of(undefined))) : of(undefined);
        return forkJoin({
            access: this.courseManagementService.getCourseTabAccess(courseId),
            user: user$,
        }).pipe(
            map(({ access, user }) => this.decideAccess(courseId, access, path, user)),
            catchError(() => of(true)),
        );
    }

    /**
     * Decides whether the given tab is accessible from the per-tab access flags and redirects to a fallback tab otherwise.
     * Kept as a single place so the access rules are not duplicated.
     */
    decideAccess(courseId: number, access: CourseTabAccess, type?: string, user?: User): boolean {
        let hasAccess: boolean;
        switch (type) {
            // Should always be accessible
            case CourseOverviewRoutePath.EXERCISES:
                hasAccess = true;
                break;
            case CourseOverviewRoutePath.LECTURES:
                hasAccess = access.lecturesEnabled ?? false;
                break;
            case CourseOverviewRoutePath.EXAMS:
                hasAccess = access.examsVisible ?? false;
                break;
            case CourseOverviewRoutePath.COMPETENCIES:
                hasAccess = access.competenciesOrPrerequisites ?? false;
                break;
            case CourseOverviewRoutePath.TUTORIAL_GROUPS:
                hasAccess = access.tutorialGroups ?? false;
                break;
            case CourseOverviewRoutePath.DASHBOARD:
                hasAccess = access.dashboardEnabled ?? false;
                break;
            case CourseOverviewRoutePath.IRIS:
                hasAccess = access.irisEnabled ?? false;
                break;
            case CourseOverviewRoutePath.FAQ:
                hasAccess = access.faqAccepted ?? false;
                break;
            case CourseOverviewRoutePath.LEARNING_PATH:
                hasAccess = access.learningPathsEnabled ?? false;
                break;
            case CourseOverviewRoutePath.COMMUNICATION:
                hasAccess = access.communicationEnabled ?? false;
                break;
            case CourseOverviewRoutePath.TRAINING:
            case CourseOverviewRoutePath.TRAINING_QUIZ:
                hasAccess = access.trainingEnabled ?? false;
                break;
            default:
                hasAccess = false;
        }
        if (!hasAccess) {
            const hasOptedOutOfAI = user?.selectedLLMUsage === LLMSelectionDecision.NO_AI;
            if (type === CourseOverviewRoutePath.DASHBOARD && access.irisEnabled && !hasOptedOutOfAI) {
                this.router.navigate([`/courses/${courseId}/iris`]);
            } else {
                this.router.navigate([`/courses/${courseId}/exercises`]);
            }
        }
        return hasAccess;
    }
}
