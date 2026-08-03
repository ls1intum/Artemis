import { describe, expect, it } from 'vitest';
import { courseManagementRoutes } from 'app/course/manage/course-management.route';
import { CourseManagementResolve } from 'app/course/manage/services/course-management-resolve.service';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { IS_AT_LEAST_INSTRUCTOR } from 'app/foundation/constants/authority.constants';
import { IrisAssessmentReviewResolver } from 'app/iris/overview/ask-user/services/iris-assessment-review-resolver.service';
import { IrisGuard } from 'app/iris/shared/iris-guard.service';

describe('courseManagementRoutes', () => {
    // The two top-level empty-path routes: the course list (no children) and the full-bleed container (with children).
    const listRoute = courseManagementRoutes.find((route) => route.path === '' && !route.children);
    const containerRoute = courseManagementRoutes.find((route) => route.path === '' && !!route.children?.length);
    const nestedCourseRoute = containerRoute?.children?.find((route) => route.path === ':courseId' && !!route.children?.length);

    const nestedChildRoute = (path: string) => nestedCourseRoute?.children?.find((route) => route.path === path);

    it('defines both the course list and the container route', () => {
        expect(listRoute?.path).toBe('');
        expect(containerRoute?.path).toBe('');
        expect(containerRoute?.children?.length ?? 0).toBeGreaterThan(0);
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

    it('provides course grading inside the management container', () => {
        expect(containerRoute!.children?.some((route) => route.path === ':courseId/grading')).toBe(true);
    });

    it('provides team pages inside the management container', () => {
        expect(containerRoute!.children?.some((route) => route.path === ':courseId/exercises/:exerciseId/teams')).toBe(true);
    });

    it('defines the Iris assessment review overview route', () => {
        const route = nestedChildRoute('iris-assessments');

        expect(route?.data).toEqual(
            expect.objectContaining({
                authorities: IS_AT_LEAST_INSTRUCTOR,
                pageTitle: 'artemisApp.iris.assessmentReviewOverview.title',
                loadWithExercises: true,
            }),
        );
        expect(route?.canActivate).toEqual([UserRouteAccessService, IrisGuard]);
        expect(route?.resolve?.['course']).toBe(CourseManagementResolve);
    });

    it('defines the regular Iris assessment review details route', () => {
        const route = nestedChildRoute('iris-assessments/:assessmentId/details');

        expect(route?.data).toEqual(
            expect.objectContaining({
                authorities: IS_AT_LEAST_INSTRUCTOR,
                pageTitle: 'artemisApp.iris.assessmentReview.title',
                inClass: false,
            }),
        );
        expect(route?.canActivate).toEqual([UserRouteAccessService, IrisGuard]);
        expect(route?.resolve?.['reviewData']).toBe(IrisAssessmentReviewResolver);
    });

    it('defines the in-class Iris assessment review routes', () => {
        const overviewRoute = nestedChildRoute('iris-in-class-assessments');
        const detailsRoute = nestedChildRoute('iris-in-class-assessments/:assessmentId/details');

        expect(overviewRoute?.data).toEqual(
            expect.objectContaining({
                authorities: IS_AT_LEAST_INSTRUCTOR,
                pageTitle: 'artemisApp.iris.assessmentReviewOverview.inClassTitle',
                loadWithExercises: true,
                showStartInClassQuizButton: true,
            }),
        );
        expect(overviewRoute?.canActivate).toEqual([UserRouteAccessService, IrisGuard]);
        expect(overviewRoute?.resolve?.['course']).toBe(CourseManagementResolve);
        expect(detailsRoute?.data?.['inClass']).toBe(true);
        expect(detailsRoute?.resolve?.['reviewData']).toBe(IrisAssessmentReviewResolver);
    });
});
