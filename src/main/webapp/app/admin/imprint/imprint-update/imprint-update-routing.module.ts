import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { Authority } from 'app/shared/constants/authority.constants';
import { ImprintUpdateComponent } from 'app/admin/imprint/imprint-update/imprint-update.component';

const routes: Routes = [
    {
        path: '',
        component: ImprintUpdateComponent,
        data: {
            authorities: [Authority.ADMIN],
            pageTitle: 'artemisApp.imprint.updateImprint',
        },
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class ImprintUpdateRoutingModule {}
