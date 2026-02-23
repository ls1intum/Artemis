import { CommonModule } from '@angular/common';
import { Component, TemplateRef, Type, computed, input, model, output, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SearchFilterComponent } from 'app/shared/search-filter/search-filter.component';
import { Table, TableLazyLoadEvent, TableModule, TablePageEvent } from 'primeng/table';

export interface ColumnDef<T> {
    field?: keyof T;
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
    imports: [CommonModule, FormsModule, TableModule, TranslateDirective, ArtemisTranslatePipe, SearchFilterComponent],
    templateUrl: './table-view.html',
    styleUrl: './table-view.scss',
})
export class TableView<T> {
    private static readonly SEARCH_DEBOUNCE_MS = 300;

    cols = input.required<ColumnDef<T>[]>();
    vals = input.required<T[]>();
    totalRows = input.required<number>();
    pageSize = model<number>(50);
    pageSizeOptions = input<number[]>([10, 20, 50, 100, 200]);
    rowActions = input<TemplateRef<{ $implicit: T }> | null>(null);
    loading = input(false);
    emptyMessageTranslation = input<string>('artemisApp.dataTable.search.noResults');
    selectedRow: T | undefined;
    currentPageReport = input<string>();

    onLazyLoad = output<TableLazyLoadEvent>();
    onRowSelect = output<any>();

    dt = viewChild.required<Table>('dt');

    private debounceTimer: ReturnType<typeof setTimeout> | undefined;

    private currentFirst = signal(0);

    itemRangeBegin = computed(() => (this.totalRows() === 0 ? 0 : Math.min(this.totalRows(), this.currentFirst() + 1)));
    itemRangeEnd = computed(() => Math.min(this.totalRows(), this.currentFirst() + this.pageSize()));

    onGlobalSearch(event: string): void {
        const value = event.trim().toLowerCase();

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
            value: col?.field && (col.field satisfies keyof T) ? data?.[col.field] : undefined,
            rowIndex,
        };
        return params;
    }

    handleLazyLoad(event: TableLazyLoadEvent): void {
        this.currentFirst.set(event.first ?? 0);
        this.onLazyLoad.emit(event);
    }

    pageChange(event: TablePageEvent): void {
        this.pageSize.set(event.rows);
        this.currentFirst.set(event.first);
    }
}
