import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { Authority } from 'app/shared/constants/authority.constants';

import { IrisModule } from 'app/iris/iris.module';
import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { IrisGuard } from 'app/iris/iris-guard.service';

const routes: Routes = [
    {
        path: '',
        loadComponent: () => import('app/iris/settings/iris-global-settings-update/iris-global-settings-update.component').then((m) => m.IrisGlobalSettingsUpdateComponent),
        data: {
            authorities: [Authority.ADMIN],
            pageTitle: 'artemisApp.iris.settings.title.global',
        },
        canActivate: [UserRouteAccessService, IrisGuard],
        canDeactivate: [PendingChangesGuard],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes), IrisModule],
    exports: [RouterModule],
})
export class IrisGlobalSettingsUpdateRoutingModule {}
