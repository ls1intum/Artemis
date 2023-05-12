import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { DataExportComponent } from 'app/core/legal/data-export/data-export.component';
import { Authority } from 'app/shared/constants/authority.constants';

const routes: Routes = [
    {
        path: 'data-export',
        component: DataExportComponent,
        data: {
            authorities: [Authority.ADMIN],
        },
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class DataExportRoutingModule {}
