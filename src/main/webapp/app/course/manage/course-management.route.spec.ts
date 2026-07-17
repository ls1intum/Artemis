import { describe, expect, it } from 'vitest';
import { courseManagementRoutes } from 'app/course/manage/course-management.route';

describe('courseManagementRoutes', () => {
    // The two top-level empty-path routes: the course list (no children) and the full-bleed container (with children).
    const listRoute = courseManagementRoutes.find((route) => route.path === '' && !route.children);
    const containerRoute = courseManagementRoutes.find((route) => route.path === '' && !!route.children?.length);

    it('defines both the course list and the container route', () => {
        expect(listRoute).toBeDefined();
        expect(containerRoute).toBeDefined();
    });

    // Regression guard for #13189: the CourseManagementContainerComponent renders a full-bleed layout (its own
    // sidebar, title bar, and module-bg content box), so it must NOT be wrapped in the app-level module-background
    // card (see app.component.html). It must therefore declare usesModuleBackground: false explicitly to override the
    // parent `course-management` route's usesModuleBackground: true (meant for the course list). Angular 22 inherits
    // parent route data down to the deepest activated child, so without this explicit false the container was wrapped
    // and shifted right, clipping content at the right edge.
    it('renders the container route full-bleed (usesModuleBackground: false)', () => {
        expect(containerRoute!.data?.['usesModuleBackground']).toBe(false);
    });

    it('keeps the module background for the course list route (inherited from the parent route)', () => {
        expect(listRoute!.data?.['usesModuleBackground']).not.toBe(false);
    });
});
