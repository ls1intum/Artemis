import { describe, expect, it } from 'vitest';
import { courseManagementRoutes } from 'app/course/manage/course-management.route';

describe('courseManagementRoutes', () => {
    const overviewRedirectRoute = courseManagementRoutes.find((route) => route.path === '' && !route.children);
    const containerRoute = courseManagementRoutes.find((route) => route.path === '' && !!route.children?.length);

    it('redirects the removed management overview to the course overview', () => {
        expect(overviewRedirectRoute?.redirectTo).toBe('/courses');
        expect(overviewRedirectRoute?.pathMatch).toBe('full');
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
});
