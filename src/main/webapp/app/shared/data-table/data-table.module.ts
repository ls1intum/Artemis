import { NgModule } from '@angular/core';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { DataTableComponent } from './data-table.component';

@NgModule({
    imports: [ArtemisSharedModule, NgxDatatableModule],
    declarations: [DataTableComponent],
    exports: [DataTableComponent],
})
export class ArtemisDataTableModule {}
