import { routes } from './iris-exercise-settings-update-route';
import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { IrisGuard } from 'app/iris/shared/iris-guard.service';
import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';
import { IrisExerciseSettingsUpdateComponent } from 'app/iris/manage/settings/iris-exercise-settings-update/iris-exercise-settings-update.component';

// Mock the dependencies to avoid loading the real services and their dependencies
jest.mock('app/core/auth/user-route-access-service', () => ({
    UserRouteAccessService: class UserRouteAccessService {},
}));
jest.mock('app/iris/shared/iris-guard.service', () => ({
    IrisGuard: class IrisGuard {},
}));
jest.mock('app/shared/guard/pending-changes.guard', () => ({
    PendingChangesGuard: class PendingChangesGuard {},
}));
jest.mock('app/iris/manage/settings/iris-exercise-settings-update/iris-exercise-settings-update.component', () => ({
    IrisExerciseSettingsUpdateComponent: class IrisExerciseSettingsUpdateComponent {},
}));

describe('IrisExerciseSettingsUpdateRoute', () => {
    it('should have the correct route configuration', async () => {
        const route = routes[0];

        expect(route.path).toBe('');
        expect(route.data).toEqual({
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.iris.settings.title.exercise',
        });
        expect(route.canActivate).toEqual([UserRouteAccessService, IrisGuard]);
        expect(route.canDeactivate).toEqual([PendingChangesGuard]);

        const component = await (route.loadComponent as () => Promise<any>)();
        expect(component).toBe(IrisExerciseSettingsUpdateComponent);
    });
});
