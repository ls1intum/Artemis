import { Component, TemplateRef, contentChild, input, model } from '@angular/core';
import { faSort } from '@fortawesome/free-solid-svg-icons';
import { PageableSearch, SearchResult, SortingOrder } from 'app/foundation/pagination/pageable-table';
import { Competency } from 'app/atlas/shared/entities/competency.model';
import { SortDirective } from 'app/foundation/sort/directive/sort.directive';
import { SortByDirective } from 'app/foundation/sort/directive/sort-by.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { NgTemplateOutlet } from '@angular/common';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';
import { HtmlForMarkdownPipe } from 'app/foundation/pipes/html-for-markdown.pipe';

@Component({
    selector: 'jhi-import-competencies-table',
    templateUrl: './import-competencies-table.component.html',
    imports: [SortDirective, SortByDirective, FaIconComponent, TranslateDirective, NgTemplateOutlet, PaginatorModule, HtmlForMarkdownPipe],
})
export class ImportCompetenciesTableComponent {
    content = input.required<SearchResult<Competency>>();
    search = model.required<PageableSearch>();
    displayPagination = input<boolean>(true);

    buttonsTemplate = contentChild(TemplateRef<any>);

    ascending = false;

    // Icons
    readonly faSort = faSort;

    /**
     * Callback function for when the user navigates through the page results
     *
     * @param pageNumber The current page number
     */
    onPageChange(pageNumber: number) {
        this.search.update((search) => ({ ...search, page: pageNumber }));
    }

    /** PrimeNG paginator page change (0-indexed) converted to the 1-indexed page used here. */
    onPaginatorPageChange(event: PaginatorState): void {
        this.onPageChange((event.page ?? 0) + 1);
    }

    /**
     * Callback function for when the user changes the sort
     * @param change an object containing the column to sort by and boolean if the sort is ascending
     */
    onSortChange(change: { predicate: string; ascending: boolean }) {
        this.search.update((search) => ({
            ...search,
            sortedColumn: change.predicate,
            sortingOrder: change.ascending ? SortingOrder.ASCENDING : SortingOrder.DESCENDING,
        }));
        this.ascending = change.ascending;
    }
}
