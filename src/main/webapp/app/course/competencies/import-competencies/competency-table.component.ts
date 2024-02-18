import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { faSort } from '@fortawesome/free-solid-svg-icons';
import { BasePageableSearch, SearchResult, SortingOrder } from 'app/shared/table/pageable-table';
import { Competency } from 'app/entities/competency.model';

@Component({
    selector: 'jhi-competency-table',
    templateUrl: './competency-table.component.html',
})
export class CompetencyTableComponent implements OnInit {
    @Input() content: SearchResult<Competency>;
    @Input() disabledIds: number[] = [];
    @Input() search: BasePageableSearch;

    @Output() searchChange = new EventEmitter<BasePageableSearch>();

    ascending: boolean;

    // Icons
    readonly faSort = faSort;

    ngOnInit() {
        this.ascending = this.search.sortingOrder === SortingOrder.ASCENDING;
    }

    /**
     * Callback function when the user navigates through the page results
     *
     * @param pageNumber The current page number
     */
    onPageChange(pageNumber: number) {
        console.log(this.content);
        console.log(pageNumber);
        this.search.page = pageNumber;
        this.searchChange.emit(this.search);
    }

    /**
     * Callback function when the user changes the sort
     *
     */
    onSortChange(change: { predicate: string; ascending: boolean }) {
        this.search.sortedColumn = change.predicate;
        this.search.sortingOrder = change.ascending ? SortingOrder.ASCENDING : SortingOrder.DESCENDING;
        this.searchChange.emit(this.search);
    }

    /**
     *
     * Gives the ID for a competency in the table, so that it can be tracked/identified by ngFor
     *
     * @param index The index of the competency in the ngFor
     * @param competency The competency
     * @returns The ID of the competency
     */
    trackId(index: number, competency: Competency): number {
        return competency.id!;
    }
}
