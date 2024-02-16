import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { faSort } from '@fortawesome/free-solid-svg-icons';
import { Column } from 'app/shared/import/import.component';
import { PageableSearch, SearchResult, SortingOrder } from 'app/shared/table/pageable-table';
import { BaseEntity } from 'app/shared/model/base-entity';
import { SortService } from 'app/shared/service/sort.service';
import { Subject } from 'rxjs';

@Component({
    selector: 'jhi-competency-table',
    templateUrl: './competency-table.component.html',
})
export class CompetencyTableComponent<T extends BaseEntity> implements OnInit {
    @Input() public disabledIds: number[] = [];
    @Input() entityName: string;

    @Input() columns: Column<T>[];
    @Input() content: SearchResult<T>;

    //callback on button press
    @Output() selectImport = new EventEmitter<T>();

    //idk
    state: PageableSearch = {
        page: 1,
        pageSize: 10,
        searchTerm: '',
        sortingOrder: SortingOrder.DESCENDING,
        sortedColumn: 'ID',
    };

    // Icons
    readonly faSort = faSort;

    protected readonly sort = new Subject<void>();

    public constructor(private sortService: SortService) {}

    ngOnInit(): void {
        this.content = { resultsOnPage: [], numberOfPages: 0 };
    }

    //TODO: solve without a getter?
    get total(): number {
        return this.content.resultsOnPage.length * this.content.numberOfPages;
    }

    //TODO: get away from getters.
    get page(): number {
        return this.state.page;
    }

    set page(page: number) {
        this.setSearchParam({ page });
    }

    get listSorting(): boolean {
        return this.state.sortingOrder === SortingOrder.ASCENDING;
    }

    /**
     * Set the list sorting direction
     *
     * @param ascending {boolean} Ascending order set
     */
    set listSorting(ascending: boolean) {
        const sortingOrder = ascending ? SortingOrder.ASCENDING : SortingOrder.DESCENDING;
        this.setSearchParam({ sortingOrder });
    }

    get sortedColumn(): string {
        return this.state.sortedColumn;
    }

    set sortedColumn(sortedColumn: string) {
        this.setSearchParam({ sortedColumn });
    }

    get searchTerm(): string {
        return this.state.searchTerm;
    }

    sortRows() {
        this.sortService.sortByProperty(this.content.resultsOnPage, this.sortedColumn, this.listSorting);
    }

    //TODO: index needed?
    //stays.
    /**
     *
     * Gives the ID for any item in the table, so that it can be tracked/identified by ngFor
     *
     * @template T
     * @param index The index of the element in the ngFor
     * @param {T} item The item itself
     * @returns The ID of the item
     */
    trackId(index: number, item: T): number {
        return item.id!;
    }

    //TODO: make this @output

    /**
     * Callback function when the user navigates through the page results
     *
     * @param pageNumber The current page number
     */
    onPageChange(pageNumber: number) {
        if (pageNumber) {
            this.page = pageNumber;
        }
    }

    // todo: make parent handle it!

    protected setSearchParam(patch: Partial<PageableSearch>) {
        Object.assign(this.state, patch);
        this.sort.next();
    }
}
