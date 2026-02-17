import { CommonModule } from '@angular/common';
import { Component, TemplateRef, Type, input, output, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Table, TableModule } from 'primeng/table';
import { InputTextModule } from 'primeng/inputtext';

export interface ColumnDef<T> {
    field: keyof T | string;
    header: string;

    // existing capabilities your HTML already expects
    width?: string;
    sort?: boolean;
    filter?: boolean;
    filterType?: string;

    // AG Grid-like renderer
    cellRenderer?: Type<unknown>;

    // optional extra params merged into renderer params
    cellRendererParams?: (row: T) => Record<string, unknown>;
}

export interface CellRendererParams<T> {
    row: T;
    col: ColumnDef<T>;
    value: T;
    rowIndex: number;

    // allow extra keys via cellRendererParams()
    [key: string]: unknown;
}

@Component({
    selector: 'jhi-table-view',
    imports: [CommonModule, FormsModule, InputTextModule, TableModule],
    templateUrl: './table-view.html',
    styleUrl: './table-view.scss',
})
export class TableView<T extends Record<string, any>> {
    dt = viewChild.required<Table>('dt');

    globalSearch = '';

    private debounceTimer: any;

    onGlobalSearchInput(e: Event) {
        const value = (e.target as HTMLInputElement).value;

        clearTimeout(this.debounceTimer);
        this.debounceTimer = setTimeout(() => {
            // reset to first page when searching
            this.dt().first = 0;

            // Important: this will emit onLazyLoad with filters updated
            this.dt().filterGlobal(value, 'contains');
        }, 300);
    }

    cols = input.required<ColumnDef<T>[]>();
    vals = input.required<T[]>();
    totalRows = input.required<number>();
    onLazyLoad = output<any>();

    // keep your existing template-based row actions
    rowActions = input<TemplateRef<{ $implicit: T }> | null>(null);

    getValue(row: T): T {
        return row;
    }

    buildRendererParams(row: T, col: ColumnDef<T>, rowIndex: number): CellRendererParams<T> {
        const base: CellRendererParams<T> = {
            row,
            col,
            value: this.getValue(row),
            rowIndex,
        };

        const extra = (col.cellRendererParams?.(row) ?? {}) as Record<string, unknown>;
        return { ...base, ...extra };
    }
}
