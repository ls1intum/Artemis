import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { courseManagementRoutes } from 'app/course/manage/course-management.route';
import { IS_AT_LEAST_INSTRUCTOR } from 'app/foundation/constants/authority.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { GocastGuard } from 'app/videosource/gocast/gocast-guard.service';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

describe('courseManagementRoutes', () => {
    const containerRoute = courseManagementRoutes.find((route) => route.path === '' && !!route.children?.length);

    it('does not handle the removed management overview inside the lazy route tree', () => {
        const overviewRoute = courseManagementRoutes.find((route) => route.path === '' && !route.children);

        expect(overviewRoute).toBeUndefined();
        expect(containerRoute).toBeDefined();
    });

    // Regression guard for #13189: the CourseManagementContainerComponent renders a full-bleed layout (its own
    // sidebar, title bar, and module-bg content box), so it must NOT be wrapped in the app-level module-background
    // card (see app.component.html). It must therefore declare usesModuleBackground: false explicitly to override the
    // parent `course-management` route's usesModuleBackground: true. Angular 22 inherits parent route data down to the
    // deepest activated child, so without this explicit false the container was wrapped
    // and shifted right, clipping content at the right edge.
    it('renders the container route full-bleed (usesModuleBackground: false)', () => {
        expect(containerRoute!.data?.['usesModuleBackground']).toBe(false);
    });

    // The course creation page brings the same frame itself (title bar above a scrolling card), so the app-level card
    // would nest a second background around it.
    it('renders the course creation route full-bleed (usesModuleBackground: false)', () => {
        const creationRoute = courseManagementRoutes.find((route) => route.path === 'new');

        expect(creationRoute!.data?.['usesModuleBackground']).toBe(false);
    });

    it('provides course grading inside the management container', () => {
        expect(containerRoute!.children?.some((route) => route.path === ':courseId/grading')).toBe(true);
    });

    it('provides team pages inside the management container', () => {
        expect(containerRoute!.children?.some((route) => route.path === ':courseId/exercises/:exerciseId/teams')).toBe(true);
    });

    it('provides instructor-only TUM.Live course connection management', () => {
        const route = containerRoute!.children?.find((candidate) => candidate.path === ':courseId/gocast-binding');

        expect(route).toBeDefined();
        expect(route!.data?.['authorities']).toEqual(IS_AT_LEAST_INSTRUCTOR);
        expect(route!.canActivate).toEqual([UserRouteAccessService, GocastGuard]);
    });

    it('blocks direct TUM.Live course connection navigation when the integration is unavailable', () => {
        TestBed.configureTestingModule({
            providers: [provideRouter([]), GocastGuard, { provide: ProfileService, useValue: { isGocastEnabled: () => false } }],
        });

        const result = TestBed.inject(GocastGuard).canActivate();
        expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/courses');
    });

    it('allows direct TUM.Live course connection navigation when the integration is available', () => {
        const route = containerRoute!.children?.find((candidate) => candidate.path === ':courseId/gocast-binding');
        expect(route?.canActivate).toContain(GocastGuard);
        TestBed.configureTestingModule({
            providers: [provideRouter([]), GocastGuard, { provide: ProfileService, useValue: { isGocastEnabled: () => true } }],
        });

        expect(TestBed.inject(GocastGuard).canActivate()).toBe(true);
    });
});
