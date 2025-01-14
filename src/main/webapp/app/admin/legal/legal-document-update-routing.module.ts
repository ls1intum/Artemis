import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { Authority } from 'app/shared/constants/authority.constants';

const routes: Routes = [
    {
        path: '',
        loadComponent: () => import('app/admin/legal/legal-document-update.component').then((m) => m.LegalDocumentUpdateComponent),
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
