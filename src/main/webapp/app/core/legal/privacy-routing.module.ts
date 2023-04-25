import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { PrivacyComponent } from 'app/core/legal/privacy.component';

const routes: Routes = [
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
