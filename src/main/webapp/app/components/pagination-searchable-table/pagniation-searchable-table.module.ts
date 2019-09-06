import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PaginationSearchableTableComponent } from 'app/components/pagination-searchable-table/pagination-searchable-table.component';
import { ArtemisSharedModule } from 'app/shared';

@NgModule({
    declarations: [PaginationSearchableTableComponent],
    imports: [CommonModule, ArtemisSharedModule],
    providers: [PaginationSearchableTableComponent],
})
export class PagniationSearchableTableModule {}
