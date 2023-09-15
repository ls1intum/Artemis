import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { ImprintComponent } from 'app/core/legal/imprint.component';

const routes: Routes = [
    {
        path: '',
        children: [
            {
                path: '',
                pathMatch: 'full',
                component: ImprintComponent,
                data: {
                    authorities: [],
                    pageTitle: 'artemisApp.legal.imprint.title',
                },
            },
            {
                path: ':fragment',
                component: ImprintComponent,
                data: {
                    authorities: [],
                    pageTitle: 'artemisApp.legal.imprint.title',
                },
            },
        ],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class ImprintRoutingModule {}
