import { Component, TemplateRef, contentChild, input, model } from '@angular/core';
import { PageableSearch, SearchResult, SortingOrder } from 'app/foundation/pagination/pageable-table';
import { Competency } from 'app/atlas/shared/entities/competency.model';
import { TumAetUiPaginatorComponent, TumAetUiTableDirective, TumAetUiTableLayoutComponent, TumAetUiTableSortEvent, TumAetUiTableSortableColumnComponent } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { NgTemplateOutlet } from '@angular/common';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { cloneWith } from 'app/foundation/util/deep-clone.util';

@Component({
    selector: 'jhi-import-competencies-table',
    templateUrl: './import-competencies-table.component.html',
    imports: [
        TumAetUiTableDirective,
        TumAetUiTableLayoutComponent,
        TumAetUiTableSortableColumnComponent,
        TumAetUiPaginatorComponent,
        TranslateDirective,
        NgTemplateOutlet,
        MarkdownDirective,
    ],
})
export class ImportCompetenciesTableComponent {
    content = input.required<SearchResult<Competency>>();
    search = model.required<PageableSearch>();
    displayPagination = input<boolean>(true);

    buttonsTemplate = contentChild(TemplateRef<{ competency: Competency }>);

    protected readonly SortingOrder = SortingOrder;

    /**
     * Callback function for when the user navigates through the page results
     *
     * @param pageNumber The current page number
     */
    onPageChange(pageNumber: number) {
        this.search.update((search) => cloneWith(search, { page: pageNumber }));
    }

    /**
     * Callback function for when the user changes the sort
     * @param change an object containing the column to sort by and the order (1 ascending, -1 descending)
     */
    onSortChange(change: TumAetUiTableSortEvent) {
        this.search.update((search) => cloneWith(search, { sortedColumn: change.field, sortingOrder: change.order === 1 ? SortingOrder.ASCENDING : SortingOrder.DESCENDING }));
    }
}
