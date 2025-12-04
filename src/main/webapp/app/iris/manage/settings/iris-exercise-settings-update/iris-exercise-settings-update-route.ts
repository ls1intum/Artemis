import { Routes } from '@angular/router';
import { IS_AT_LEAST_INSTRUCTOR } from 'app/shared/constants/authority.constants';
import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

import { IrisGuard } from 'app/iris/shared/iris-guard.service';

export const routes: Routes = [
    {
        path: '',
        loadComponent: () =>
            import('app/iris/manage/settings/iris-exercise-settings-update/iris-exercise-settings-update.component').then((m) => m.IrisExerciseSettingsUpdateComponent),
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            pageTitle: 'artemisApp.iris.settings.title.exercise',
        },
        canActivate: [UserRouteAccessService, IrisGuard],
        canDeactivate: [PendingChangesGuard],
    },
];
