import { CommonModule } from '@angular/common';
import { Component, TemplateRef, Type, input, output, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Table, TableModule } from 'primeng/table';
import { InputTextModule } from 'primeng/inputtext';
import { TranslateDirective } from 'app/shared/language/translate.directive';

export interface ColumnDef<T> {
    field: keyof T | string;
    header?: string;
    headerKey?: string;
    width?: string;
    sort?: boolean;
    filter?: boolean;
    filterType?: string;
    cellRenderer?: Type<unknown>;
}

export interface CellRendererParams<T> {
    data: T;
    col: ColumnDef<T>;
    value: any;
    rowIndex: number;
}

@Component({
    selector: 'jhi-table-view',
    imports: [CommonModule, FormsModule, InputTextModule, TableModule, TranslateDirective],
    templateUrl: './table-view.html',
    styleUrl: './table-view.scss',
})
export class TableView<T extends Record<string, any>> {
    private static readonly SEARCH_DEBOUNCE_MS = 300;

    cols = input.required<ColumnDef<T>[]>();
    vals = input.required<T[]>();
    totalRows = input.required<number>();
    pageSize = input<number>(50);
    pageSizeOptions = input<number[]>([10, 50, 100]);
    rowActions = input<TemplateRef<{ $implicit: T }> | null>(null);
    loading = input(false);
    emptyMessageTranslation = input<string>('artemisApp.dataTable.search.noResults');
    selectedRow: T | undefined;

    onLazyLoad = output<any>();
    onRowSelect = output<any>();

    dt = viewChild.required<Table>('dt');

    globalSearch = '';
    private debounceTimer: ReturnType<typeof setTimeout> | undefined;

    onGlobalSearchInput(event: Event): void {
        const value = (event.target as HTMLInputElement).value;

        clearTimeout(this.debounceTimer);
        this.debounceTimer = setTimeout(() => {
            this.dt().first = 0;
            this.dt().filterGlobal(value, 'contains');
        }, TableView.SEARCH_DEBOUNCE_MS);
    }

    buildRendererParams(data: T, col: ColumnDef<T>, rowIndex: number): CellRendererParams<T> {
        const params: CellRendererParams<T> = {
            data,
            col,
            value: data?.[col.field],
            rowIndex,
        };
        return params;
    }
}
