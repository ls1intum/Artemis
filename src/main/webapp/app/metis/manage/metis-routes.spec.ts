import { Type } from '@angular/core';
import { Route } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { routes } from 'app/programming/manage/programming-exercise-management.route';
import { MetisGuard } from 'app/metis/shared/metis-guard.service';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { IS_AT_LEAST_EDITOR, IS_AT_LEAST_TUTOR } from 'app/foundation/constants/authority.constants';
import { ProgrammingExerciseResolve } from 'app/programming/manage/services/programming-exercise-resolve.service';

/** The lazy component of a route, loaded the way the router would load it. */
async function loadComponent(route: Route): Promise<Type<unknown>> {
    const loaded = await (route.loadComponent as () => Promise<Type<unknown>>)();
    return loaded;
}

describe('Metis management routes', () => {
    const dashboardRoute = routes.find((route) => route.path === 'programming-exercises/:exerciseId/comprehension-check');
    const reviewRoute = routes.find((route) => route.path === 'programming-exercises/:exerciseId/comprehension-check/review');

    it('mounts the dashboard for tutors behind the user access and module guards', () => {
        expect(dashboardRoute).toBeDefined();
        expect(dashboardRoute!.data?.['authorities']).toBe(IS_AT_LEAST_TUTOR);
        expect(dashboardRoute!.data?.['pageTitle']).toBe('artemisApp.metis.dashboard.title');
        expect(dashboardRoute!.canActivate).toEqual([UserRouteAccessService, MetisGuard]);
        expect(dashboardRoute!.resolve?.['exercise']).toBe(ProgrammingExerciseResolve);
    });

    it('mounts the question review for editors behind the user access and module guards', () => {
        expect(reviewRoute).toBeDefined();
        expect(reviewRoute!.data?.['authorities']).toBe(IS_AT_LEAST_EDITOR);
        expect(reviewRoute!.data?.['pageTitle']).toBe('artemisApp.metis.review.title');
        expect(reviewRoute!.canActivate).toEqual([UserRouteAccessService, MetisGuard]);
        expect(reviewRoute!.resolve?.['exercise']).toBe(ProgrammingExerciseResolve);
    });

    it('lazy-loads the two Metis pages', async () => {
        const dashboardComponent = await loadComponent(dashboardRoute!);
        const reviewComponent = await loadComponent(reviewRoute!);

        expect(dashboardComponent.name).toBe('MetisDashboardComponent');
        expect(reviewComponent.name).toBe('MetisQuestionReviewComponent');
    });
});
