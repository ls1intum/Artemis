import { Exercise } from 'app/entities/exercise.model';
import { debounceTime, switchMap, tap } from 'rxjs/operators';
import { Subject } from 'rxjs';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { PageableSearch, SearchResult, SortingOrder } from 'app/shared/table/pageable-table';
import { SortService } from 'app/shared/service/sort.service';
import { faCheck, faSort } from '@fortawesome/free-solid-svg-icons';

export enum TableColumn {
    ID = 'ID',
    TITLE = 'TITLE',
    COURSE_TITLE = 'COURSE_TITLE',
}

export abstract class ExerciseImportComponent<T extends Exercise> {
    private search = new Subject<void>();
    private sort = new Subject<void>();

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

    isCourseFilter = true;
    isExamFilter = true;

    protected constructor(private sortService: SortService, private activeModal: NgbActiveModal) {}

    protected init(pagingService: any): void {
        this.content = { resultsOnPage: [], numberOfPages: 0 };

        this.performSearch(this.sort, 0, pagingService);
        this.performSearch(this.search, 300, pagingService);
    }

    /** Method to perform the search based on a search subject
     *
     * @param searchSubject The search subject which we use to search.
     * @param debounce The delay we apply to deley the feedback / wait for input
     */
    protected performSearch(searchSubject: Subject<void>, debounce: number, pagingService: any) {
        // TODO better pagingService type?
        searchSubject
            .pipe(
                debounceTime(debounce),
                tap(() => (this.loading = true)),
                switchMap(() => pagingService.searchForExercises(this.state, this.isCourseFilter, this.isExamFilter)),
            )
            .subscribe((resp: SearchResult<T>) => {
                this.content = resp;
                this.loading = false;
                this.total = resp.numberOfPages * this.state.pageSize;
            });
    }

    set page(page: number) {
        this.setSearchParam({ page });
    }

    get page(): number {
        return this.state.page;
    }

    sortRows() {
        this.sortService.sortByProperty(this.content.resultsOnPage, this.sortedColumn, this.listSorting);
    }

    /**
     * Gives the ID for any exercise in the table, so that it can be tracked/identified by ngFor
     *
     * @param index The index of the element in the ngFor
     * @param item The exercise itself
     * @returns The ID of the exercise
     */
    trackId(index: number, item: T): number {
        return item.id!;
    }

    /** Set the list sorting direction
     *
     * @param ascending {boolean} Ascending order set
     */
    set listSorting(ascending: boolean) {
        const sortingOrder = ascending ? SortingOrder.ASCENDING : SortingOrder.DESCENDING;
        this.setSearchParam({ sortingOrder });
    }

    get listSorting(): boolean {
        return this.state.sortingOrder === SortingOrder.ASCENDING;
    }

    private setSearchParam(patch: Partial<PageableSearch>) {
        Object.assign(this.state, patch);
        this.sort.next();
    }

    set sortedColumn(sortedColumn: string) {
        this.setSearchParam({ sortedColumn });
    }

    get sortedColumn(): string {
        return this.state.sortedColumn;
    }

    set searchTerm(searchTerm: string) {
        this.state.searchTerm = searchTerm;
        this.search.next();
    }

    get searchTerm(): string {
        return this.state.searchTerm;
    }

    onCourseFilterChange() {
        this.isCourseFilter = !this.isCourseFilter;
        this.search.next();
    }

    onExamFilterChange() {
        this.isExamFilter = !this.isExamFilter;
        this.search.next();
    }

    /**
     * Closes the modal in which the import component is opened. Gives the selected exercise as a result.
     *
     * @param exercise The exercise which was selected by the user for the import.
     */
    openImport(exercise: T) {
        this.activeModal.close(exercise);
    }

    /**
     * Closes the modal in which the import component is opened by dismissing it
     */
    clear() {
        this.activeModal.dismiss('cancel');
    }

    /** Callback function when the user navigates through the page results
     *
     * @param pageNumber The current page number
     */
    onPageChange(pageNumber: number) {
        if (pageNumber) {
            this.page = pageNumber;
        }
    }
}
