import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { DataTableComponent } from './data-table.component';
import { SortByPipe } from 'app/shared/pipes/sort-by.pipe';

@NgModule({
    imports: [ArtemisSharedModule, NgxDatatableModule],
    declarations: [DataTableComponent],
    exports: [DataTableComponent],
    providers: [SortByPipe],
})
export class ArtemisDataTableModule {}
