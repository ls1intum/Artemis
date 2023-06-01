import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { PrivacyComponent } from 'app/core/legal/privacy.component';
import { DataExportComponent } from 'app/core/legal/data-export/data-export.component';
import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

const routes: Routes = [
    {
        path: 'data-exports/:id',
        component: DataExportComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.dataExport.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'data-exports',
        component: DataExportComponent,
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
                component: PrivacyComponent,
                data: {
                    authorities: [],
                    pageTitle: 'legal.privacy.title',
                },
            },
            {
                path: ':fragment',
                component: PrivacyComponent,
                data: {
                    authorities: [],
                    pageTitle: 'legal.privacy.title',
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
