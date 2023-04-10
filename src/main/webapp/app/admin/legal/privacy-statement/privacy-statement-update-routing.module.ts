import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { PrivacyStatementUpdateComponent } from 'app/admin/legal/privacy-statement/privacy-statement-update.component';
import { Authority } from 'app/shared/constants/authority.constants';

const routes: Routes = [
    {
        path: '',
        component: PrivacyStatementUpdateComponent,
        data: {
            authorities: [Authority.ADMIN],
            pageTitle: 'artemisApp.privacyStatement.updatePrivacyStatement',
        },
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class PrivacyStatementUpdateRoutingModule {}
