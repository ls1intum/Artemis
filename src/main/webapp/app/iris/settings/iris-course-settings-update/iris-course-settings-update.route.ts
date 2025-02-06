import { Routes } from '@angular/router';
import { Authority } from 'app/shared/constants/authority.constants';

import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { IrisGuard } from 'app/iris/iris-guard.service';

const routes: Routes = [
    {
        path: '',
        loadComponent: () => import('app/iris/settings/iris-course-settings-update/iris-course-settings-update.component').then((m) => m.IrisCourseSettingsUpdateComponent),
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.iris.settings.title.course',
        },
        canActivate: [UserRouteAccessService, IrisGuard],
        canDeactivate: [PendingChangesGuard],
    },
];
export { routes };
