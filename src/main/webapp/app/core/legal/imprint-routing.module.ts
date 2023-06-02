import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { ImprintComponent } from 'app/core/legal/imprint.component';

const routes: Routes = [
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
export class ImprintRoutingModule {}
