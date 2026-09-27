import { Service, inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router, UrlTree } from '@angular/router';
import { Observable, catchError, map, of } from 'rxjs';
import { CourseAvailableTabs } from 'app/course/shared/entities/course-available-tabs.model';
import { CourseAvailableTabsService } from 'app/course/overview/services/course-available-tabs.service';
import { CourseOverviewRoutePath } from 'app/course/overview/courses.route';

@Service()
export class CourseOverviewGuard implements CanActivate {
    private courseAvailableTabsService = inject(CourseAvailableTabsService);
    private router = inject(Router);

    /**
     * Check if the client can activate a course overview route.
     *
     * The guard decides from the course's available tabs BEFORE the route activates, so an unavailable tab never briefly
     * mounts. The tabs come from {@link CourseAvailableTabsService}, which scopes them to one navigation: the guard and
     * the container that follows it share a single lightweight request, and the next tab selection asks again. On a load
     * error (e.g. 403 for an unregistered user) activation is allowed; the container's loadCourse then handles it
     * (course registration redirect / alert).
     *
     * @return true if the client is allowed to access the route, a redirect to the exercises tab if the tab is unavailable,
     * and false if the route carries no course id or path
     */
    canActivate(route: ActivatedRouteSnapshot): Observable<boolean | UrlTree> {
        const courseIdString = route.parent?.paramMap.get('courseId');
        if (!courseIdString) {
            return of(false);
        }
        const courseId = parseInt(courseIdString, 10);

        const path = route.routeConfig?.path;
        if (!path) {
            return of(false);
        }
        return this.courseAvailableTabsService.loadIfNeeded(courseId).pipe(
            map((tabs) => this.decideAccess(courseId, tabs, path)),
            catchError(() => of(true)),
        );
    }

    /**
     * Decides whether the given tab may be opened, and returns a redirect to the exercises tab otherwise.
     * Kept as a single place so the rules are not duplicated.
     */
    decideAccess(courseId: number, tabs: CourseAvailableTabs, type?: string): true | UrlTree {
        let hasAccess: boolean;
        switch (type) {
            // Should always be accessible
            case CourseOverviewRoutePath.EXERCISES:
                hasAccess = true;
                break;
            case CourseOverviewRoutePath.LECTURES:
                hasAccess = tabs.lectures;
                break;
            case CourseOverviewRoutePath.EXAMS:
                hasAccess = tabs.exams;
                break;
            case CourseOverviewRoutePath.COMPETENCIES:
                hasAccess = tabs.competencies;
                break;
            case CourseOverviewRoutePath.TUTORIAL_GROUPS:
                hasAccess = tabs.tutorialGroups;
                break;
            case CourseOverviewRoutePath.IRIS:
                hasAccess = tabs.iris;
                break;
            case CourseOverviewRoutePath.FAQ:
                hasAccess = tabs.faq;
                break;
            case CourseOverviewRoutePath.LEARNING_PATH:
                hasAccess = tabs.learningPaths;
                break;
            case CourseOverviewRoutePath.COMMUNICATION:
                hasAccess = tabs.communication;
                break;
            case CourseOverviewRoutePath.TRAINING:
            case CourseOverviewRoutePath.TRAINING_QUIZ:
                hasAccess = tabs.training;
                break;
            default:
                hasAccess = false;
        }
        return hasAccess || this.router.createUrlTree([`/courses/${courseId}/exercises`]);
    }
}
