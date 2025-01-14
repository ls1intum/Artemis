import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { NgxDatatableModule } from '@siemens/ngx-datatable';
import { DataTableComponent } from './data-table.component';

@NgModule({
    imports: [ArtemisSharedModule, NgxDatatableModule, DataTableComponent],
    exports: [DataTableComponent],
})
export class ArtemisDataTableModule {}
