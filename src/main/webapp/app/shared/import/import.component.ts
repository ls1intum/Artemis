import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { faCheck, faSort } from '@fortawesome/free-solid-svg-icons';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { PagingService } from 'app/exercises/shared/manage/paging.service';
import { BaseEntity } from 'app/shared/model/base-entity';
import { SortService } from 'app/shared/service/sort.service';
import { PageableSearch, SearchResult, SortingOrder } from 'app/shared/table/pageable-table';
import { Subject, debounceTime, switchMap, tap } from 'rxjs';

// TODO remove this enum and use a string literal type instead (like done for the exercises)
export enum TableColumn {
    ID = 'ID',
    TITLE = 'TITLE',
    COURSE_TITLE = 'COURSE_TITLE',
    SEMESTER = 'SEMESTER',
}

/**
 * An abstract component intended for cases where a resource needs to be imported from one course into another.
 *
 * @template T generic class parameter of the entity that gets imported
 */
@Component({ template: '' })
export abstract class ImportComponent<T extends BaseEntity> implements OnInit {
    loading = false;
    content: SearchResult<T>;
    total = 0;
    state: PageableSearch = {
        page: 1,
        pageSize: 10,
        searchTerm: '',
        sortingOrder: SortingOrder.DESCENDING,
        sortedColumn: TableColumn.ID,
    };

    // Icons
    readonly faSort = faSort;
    readonly faCheck = faCheck;
    protected readonly search = new Subject<void>();
    protected readonly sort = new Subject<void>();

    protected constructor(
        protected router: Router,
        protected pagingService: PagingService<T>,
        private sortService: SortService,
        protected activeModal: NgbActiveModal,
    ) {}

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
                switchMap(() => this.pagingService.search(this.state, this.createOptions())),
            )
            .subscribe((resp: SearchResult<T>) => {
                this.content = resp;
                this.loading = false;
                this.total = resp.numberOfPages * this.state.pageSize;
            });
    }

    protected createOptions(): object | undefined {
        return undefined;
    }

    protected setSearchParam(patch: Partial<PageableSearch>) {
        Object.assign(this.state, patch);
        this.sort.next();
    }
}
