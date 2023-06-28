import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { LegalDocumentUpdateComponent } from 'app/admin/legal/legal-document-update.component';
import { Authority } from 'app/shared/constants/authority.constants';

const routes: Routes = [
    {
        path: '',
        component: LegalDocumentUpdateComponent,
        data: {
            authorities: [Authority.ADMIN],
        },
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class LegalDocumentUpdateRoutingModule {}
