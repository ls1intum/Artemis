import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

const routes: Routes = [
    {
        path: '',
        children: [
            {
                path: '',
                pathMatch: 'full',
                loadComponent: () => import('app/core/legal/imprint.component').then((m) => m.ImprintComponent),
                data: {
                    authorities: [],
                    pageTitle: 'artemisApp.legal.imprint.title',
                },
            },
            {
                path: ':fragment',
                loadComponent: () => import('app/core/legal/imprint.component').then((m) => m.ImprintComponent),
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
