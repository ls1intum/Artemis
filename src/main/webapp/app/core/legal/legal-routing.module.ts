import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { ImprintComponent } from 'app/core/legal/imprint.component';
import { PrivacyComponent } from 'app/core/legal/privacy.component';

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
