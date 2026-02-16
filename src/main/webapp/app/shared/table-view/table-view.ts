import { CommonModule } from '@angular/common';
import { Component, TemplateRef, input, output, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Table, TableModule } from 'primeng/table';
import { InputTextModule } from 'primeng/inputtext';

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
    cols = input.required<any[]>();
    vals = input.required<any[]>();
    totalRows = input.required<number>();
    onLazyLoad = output<any>();
    rowActions = input<TemplateRef<{ $implicit: T }> | null>(null);
}
