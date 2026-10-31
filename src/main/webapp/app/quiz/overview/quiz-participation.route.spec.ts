import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { routes } from './quiz-participation.route';

describe('quiz participation routes', () => {
    it('keeps live mode distinct from practice and its optional participation and submission states', () => {
        expect(routes.map(({ path, data }) => ({ path, mode: data?.mode }))).toEqual([
            { path: 'live', mode: 'live' },
            { path: 'practice', mode: 'practice' },
            { path: 'practice/:participationId', mode: 'practice' },
            { path: 'practice/:participationId/submission/:submissionId', mode: 'practice' },
        ]);
        for (const route of routes) {
            expect(route.canActivate).toEqual([UserRouteAccessService]);
            expect(route.data?.authorities).toEqual([]);
            expect(route.data?.pageTitle).toBe('overview.exercises');
        }
    });
});
