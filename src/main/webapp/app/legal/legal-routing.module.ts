import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { PrivacyComponent } from 'app/legal/privacy/privacy.component';

const routes: Routes = [
    {
        path: 'datenschutz',
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
export class LegalRoutingModule {}
