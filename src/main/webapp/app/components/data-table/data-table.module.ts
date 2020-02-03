import { NgModule } from '@angular/core';

import { ArtemisSharedModule } from 'app/shared';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { DataTableComponent } from './data-table.component';

@NgModule({
    imports: [ArtemisSharedModule, NgxDatatableModule],
    declarations: [DataTableComponent],
    exports: [DataTableComponent],
})
export class ArtemisDataTableModule {}
