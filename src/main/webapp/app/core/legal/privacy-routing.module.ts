import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

const routes: Routes = [
    {
        path: 'data-exports/:id',
        loadComponent: () => import('app/core/legal/data-export/data-export.component').then((m) => m.DataExportComponent),
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.dataExport.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'data-exports',
        loadComponent: () => import('app/core/legal/data-export/data-export.component').then((m) => m.DataExportComponent),
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.dataExport.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: '',
        children: [
            {
                path: '',
                pathMatch: 'full',
                loadComponent: () => import('app/core/legal/privacy.component').then((m) => m.PrivacyComponent),
                data: {
                    authorities: [],
                    pageTitle: 'artemisApp.legal.privacyStatement.title',
                },
            },
            {
                path: ':fragment',
                loadComponent: () => import('app/core/legal/privacy.component').then((m) => m.PrivacyComponent),
                data: {
                    authorities: [],
                    pageTitle: 'artemisApp.legal.privacyStatement.title',
                },
            },
        ],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class PrivacyRoutingModule {}
