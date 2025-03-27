import { Component, ContentChild, EventEmitter, Input, OnInit, Output, TemplateRef } from '@angular/core';
import { faSort } from '@fortawesome/free-solid-svg-icons';
import { PageableSearch, SearchResult, SortingOrder } from 'app/shared/table/pageable-table';
import { Competency } from 'app/atlas/shared/entities/competency.model';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgTemplateOutlet } from '@angular/common';
import { NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';

@Component({
    selector: 'jhi-import-competencies-table',
    templateUrl: './import-competencies-table.component.html',
    imports: [SortDirective, SortByDirective, FaIconComponent, TranslateDirective, NgTemplateOutlet, NgbPagination, HtmlForMarkdownPipe],
})
export class ImportCompetenciesTableComponent implements OnInit {
    @Input() content: SearchResult<Competency>;
    @Input() search: PageableSearch;
    @Input() displayPagination = true;

    @Output() searchChange = new EventEmitter<PageableSearch>();

    @ContentChild(TemplateRef) buttonsTemplate: TemplateRef<any>;

    ascending: boolean;

    // Icons
    readonly faSort = faSort;

    ngOnInit() {
        this.ascending = this.search.sortingOrder === SortingOrder.ASCENDING;
    }

    /**
     * Callback function for when the user navigates through the page results
     *
     * @param pageNumber The current page number
     */
    onPageChange(pageNumber: number) {
        this.search.page = pageNumber;
        this.searchChange.emit(this.search);
    }

    /**
     * Callback function for when the user changes the sort
     * @param change an object containing the column to sort by and boolean if the sort is ascending
     */
    onSortChange(change: { predicate: string; ascending: boolean }) {
        this.search.sortedColumn = change.predicate;
        this.search.sortingOrder = change.ascending ? SortingOrder.ASCENDING : SortingOrder.DESCENDING;
        this.searchChange.emit(this.search);
    }
}
