import { Component, Input, OnInit } from '@angular/core';
import { Subject } from 'rxjs';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { PageableSearch, SearchResult, SortingOrder } from 'app/shared/table/pageable-table';
import { SortService } from 'app/shared/service/sort.service';
import { Router } from '@angular/router';
import { faCheck, faSort } from '@fortawesome/free-solid-svg-icons';
import { BaseEntity } from 'app/shared/model/base-entity';
import { CompetencyPagingService } from 'app/course/competencies/competency-paging.service';

export enum TableColumn {
    ID = 'ID',
    TITLE = 'TITLE',
    COURSE_TITLE = 'COURSE_TITLE',
    SEMESTER = 'SEMESTER',
}

// TODO: Generalize this component further to make it compatible with all potential implementations (ExerciseImportComponent, LectureImportComponent, etc.)
/**
 * An abstract component intended for cases where a resource needs to be imported from one course into another.
 *
 * @template T
 */
@Component({ template: '' })
export abstract class ImportComponent<T extends BaseEntity> implements OnInit {
    readonly column = TableColumn;
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
    faSort = faSort;
    faCheck = faCheck;
    private search = new Subject<void>();
    private sort = new Subject<void>();

    @Input() public disabledIds: number[];

    constructor(private router: Router, public pagingService: CompetencyPagingService, private sortService: SortService, private activeModal: NgbActiveModal) {}

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
    abstract performSearch(searchSubject: Subject<void>, debounce: number): void;

    private setSearchParam(patch: Partial<PageableSearch>) {
        Object.assign(this.state, patch);
        this.sort.next();
    }
}
