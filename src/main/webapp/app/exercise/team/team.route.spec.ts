import { describe, expect, it } from 'vitest';
import { teamManagementRoute } from 'app/exercise/team/team.route';
import { IS_AT_LEAST_TUTOR } from 'app/foundation/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

describe('team management routes', () => {
    it('protects the team list and detail management routes with tutor authority', () => {
        expect(teamManagementRoute.map((route) => route.path)).toEqual(['', ':teamId']);

        for (const route of teamManagementRoute) {
            expect(route.data?.['authorities']).toBe(IS_AT_LEAST_TUTOR);
            expect(route.canActivate).toContain(UserRouteAccessService);
        }
    });
});
