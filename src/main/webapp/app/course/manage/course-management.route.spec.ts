import { Injector } from '@angular/core';
import { Router } from '@angular/router';
import { describe, expect, it, vi } from 'vitest';
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
        const navigate = vi.fn();
        const injector = Injector.create({
            providers: [GocastGuard, { provide: ProfileService, useValue: { isGocastEnabled: () => false } }, { provide: Router, useValue: { navigate } }],
        });

        expect(injector.get(GocastGuard).canActivate()).toBe(false);
        expect(navigate).toHaveBeenCalledWith(['/courses']);
    });

    it('allows direct TUM.Live course connection navigation when the integration is available', () => {
        const route = containerRoute!.children?.find((candidate) => candidate.path === ':courseId/gocast-binding');
        expect(route?.canActivate).toContain(GocastGuard);
        const navigate = vi.fn();
        const injector = Injector.create({
            providers: [GocastGuard, { provide: ProfileService, useValue: { isGocastEnabled: () => true } }, { provide: Router, useValue: { navigate } }],
        });

        expect(injector.get(GocastGuard).canActivate()).toBe(true);
        expect(navigate).not.toHaveBeenCalled();
    });
});
