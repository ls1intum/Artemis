import { CommonModule } from '@angular/common';
import { Component, TemplateRef, input, output } from '@angular/core';
import { TableModule } from 'primeng/table';

@Component({
    selector: 'jhi-table-view',
    imports: [CommonModule, TableModule],
    templateUrl: './table-view.html',
    styleUrl: './table-view.scss',
})
export class TableView<T extends Record<string, any>> {
    cols = input.required<any[]>();
    vals = input.required<any[]>();
    totalRows = input.required<number>();
    onLazyLoad = output<any>();
    rowActions = input<TemplateRef<{ $implicit: T }> | null>(null);
}
