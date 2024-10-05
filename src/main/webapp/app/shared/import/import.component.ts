import { Component, Input, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { faCheck, faSort } from '@fortawesome/free-solid-svg-icons';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { PagingService } from 'app/exercises/shared/manage/paging.service';
import { BaseEntity } from 'app/shared/model/base-entity';
import { SortService } from 'app/shared/service/sort.service';
import { SearchResult, SearchTermPageableSearch, SortingOrder } from 'app/shared/table/pageable-table';
import { Subject, debounceTime, switchMap, tap } from 'rxjs';

/**
 * An abstract component intended for cases where a resource needs to be imported from one course into another.
 *
 * @template T generic class parameter of the entity that gets imported
 */

export type Column<T extends BaseEntity> = {
    name: string;
    getProperty(entity: T): string | undefined;
};

@Component({ template: '' })
export abstract class ImportComponent<T extends BaseEntity> implements OnInit {
    protected router = inject(Router);
    private sortService = inject(SortService);
    protected activeModal = inject(NgbActiveModal);
    protected pagingService? = inject<PagingService<T>>(PagingService);

    loading = false;
    content: SearchResult<T>;
    total = 0;
    state: SearchTermPageableSearch = {
        page: 1,
        pageSize: 10,
        searchTerm: '',
        sortingOrder: SortingOrder.DESCENDING,
        sortedColumn: 'ID',
    };

    // These two attributes should be set when using the common template (import.component.html)
    entityName: string;
    columns: Column<T>[];

    @Input() public disabledIds: number[] = [];

    // Icons
    readonly faSort = faSort;
    readonly faCheck = faCheck;
    protected readonly search = new Subject<void>();
    protected readonly sort = new Subject<void>();

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

    set searchTerm(searchTerm: string) {
        this.state.searchTerm = searchTerm;
        this.search.next();
    }

    ngOnInit(): void {
        this.content = { resultsOnPage: [], numberOfPages: 0 };

        this.performSearch(this.sort, 0);
        this.performSearch(this.search, 300);
    }

    sortRows() {
        this.sortService.sortByProperty(this.content.resultsOnPage, this.sortedColumn, this.listSorting);
    }

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

    /**
     * Closes the modal in which the import component is opened. Gives the selected item as a result.
     *
     * @param item The item which was selected by the user for the import.
     */
    selectImport(item: T) {
        this.activeModal.close(item);
    }

    /**
     * Closes the modal in which the import component is opened by dismissing it
     */
    clear() {
        this.activeModal.dismiss('cancel');
    }

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

    /**
     * Method to perform the search based on a search subject
     *
     * @param searchSubject The search subject which we use to search.
     * @param debounce The delay we apply to delay the feedback / wait for input
     */
    performSearch(searchSubject: Subject<void>, debounce: number) {
        searchSubject
            .pipe(
                debounceTime(debounce),
                tap(() => (this.loading = true)),
                switchMap(() => this.pagingService!.search(this.state, this.createOptions())),
            )
            .subscribe((resp: SearchResult<T>) => {
                this.content = resp;
                this.loading = false;
                this.total = resp.numberOfPages * this.state.pageSize;
                this.onSearchResult();
            });
    }

    /**
     * This method is used to create additional options passed to the paging service.
     */
    protected createOptions(): object | undefined {
        return undefined;
    }

    /**
     * This method is called after retrieving a result from the paging service.
     * Used to perform some special logic with the search result (e.g. calculating the submission size for the example submission import)
     */
    protected onSearchResult(): void {}

    protected setSearchParam(patch: Partial<SearchTermPageableSearch>) {
        Object.assign(this.state, patch);
        this.sort.next();
    }
}
