import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { IS_AT_LEAST_STUDENT } from 'app/foundation/constants/authority.constants';
import { PendingChangesGuard } from 'app/foundation/guard/pending-changes.guard';
import { routes } from './modeling-participation.route';
import { modelingExerciseSplitPanelRoute } from './modeling-exercise-split-panel.route';

describe('modeling submission entry points', () => {
    it.each([
        { entry: routes, title: 'artemisApp.modelingExercise.home.title' },
        { entry: modelingExerciseSplitPanelRoute, title: 'overview.exercises' },
    ])('keeps the three editor states, student access and unsaved changes guard for $title', ({ entry, title }) => {
        expect(entry.map((route) => route.path)).toEqual([
            'participate/:participationId',
            'participate/:participationId/submission/:submissionId',
            'participate/:participationId/submission/:submissionId/result/:resultId',
        ]);
        for (const route of entry) {
            expect(route.data).toEqual({ authorities: IS_AT_LEAST_STUDENT, pageTitle: title });
            expect(route.canActivate).toEqual([UserRouteAccessService]);
            expect(route.canDeactivate).toEqual([PendingChangesGuard]);
            expect(route.loadComponent).toBeTypeOf('function');
        }
    });
});
