import { NgModule } from '@angular/core';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';

import { DataTableComponent } from './data-table.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule, NgxDatatableModule],
    declarations: [DataTableComponent],
    exports: [DataTableComponent],
})
export class ArtemisDataTableModule {}
