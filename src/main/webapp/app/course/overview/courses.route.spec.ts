import { describe, expect, it } from 'vitest';
import { courseRoutes } from 'app/course/overview/courses.route';
import { IS_AT_LEAST_STUDENT } from 'app/foundation/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

describe('course routes', () => {
    const courseOverviewRoute = courseRoutes.find((route) => route.path === ':courseId');

    it('only provides the team detail page in the student course area', () => {
        const teamDetailRoute = courseOverviewRoute?.children?.find((route) => route.path === 'exercises/:exerciseId/teams/:teamId');

        expect(teamDetailRoute).toBeDefined();
        expect(teamDetailRoute?.data?.['authorities']).toBe(IS_AT_LEAST_STUDENT);
        expect(teamDetailRoute?.canActivate).toContain(UserRouteAccessService);
        expect(courseOverviewRoute?.children?.some((route) => route.path === 'exercises/:exerciseId/teams')).toBe(false);
    });
});
