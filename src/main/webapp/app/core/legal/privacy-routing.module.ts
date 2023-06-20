import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { PrivacyComponent } from 'app/core/legal/privacy.component';
import { DataExportComponent } from 'app/core/legal/data-export/data-export.component';

const routes: Routes = [
    {
        path: 'data-export',
        component: DataExportComponent,
        data: {
            authorities: [],
            pageTitle: 'artemisApp.dataExport.title',
        },
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
                    pageTitle: 'artemisApp.legal.privacyStatement.title',
                },
            },
            {
                path: ':fragment',
                component: PrivacyComponent,
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
