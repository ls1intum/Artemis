import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { PrivacyComponent } from 'app/core/legal/privacy.component';
import { ImprintComponent } from 'app/core/legal/imprint.component';
import { DataExportComponent } from 'app/core/legal/data-export/data-export.component';
import { Authority } from 'app/shared/constants/authority.constants';

const routes: Routes = [
    {
        path: 'privacy',
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
    {
        path: 'data-export',
        pathMatch: 'full',
        component: DataExportComponent,
        data: {
            // for now, only accessible for admins as feature is not complete yet.
            authorities: [Authority.ADMIN],
            pageTitle: 'artemisApp.dataExport.title',
        },
    },
    {
        path: 'imprint',
        children: [
            {
                path: '',
                pathMatch: 'full',
                component: ImprintComponent,
                data: {
                    authorities: [],
                    pageTitle: 'legal.imprint.title',
                },
            },
            {
                path: ':fragment',
                component: ImprintComponent,
                data: {
                    authorities: [],
                    pageTitle: 'legal.imprint.title',
                },
            },
        ],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class LegalRoutingModule {}
