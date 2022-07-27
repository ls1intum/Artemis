import { Component, OnInit } from '@angular/core';
import { debounceTime, switchMap, tap } from 'rxjs/operators';
import { Subject } from 'rxjs';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { PageableSearch, SearchResult, SortingOrder } from 'app/shared/table/pageable-table';
import { ProgrammingExercisePagingService } from 'app/exercises/programming/manage/services/programming-exercise-paging.service';
import { SortService } from 'app/shared/service/sort.service';
import { faSort } from '@fortawesome/free-solid-svg-icons';

export enum TableColumn {
    ID = 'ID',
    TITLE = 'TITLE',
    PROGRAMMING_LANGUAGE = 'PROGRAMMING_LANGUAGE',
    COURSE_TITLE = 'COURSE_TITLE',
}

@Component({
    selector: 'jhi-programming-exercise-import',
    templateUrl: './programming-exercise-import.component.html',
})
export class ProgrammingExerciseImportComponent implements OnInit {
    readonly column = TableColumn;

    private search = new Subject<void>();
    private sort = new Subject<void>();

    loading = false;
    content: SearchResult<ProgrammingExercise>;
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

    isCourseFilter = true;
    isExamFilter = true;

    constructor(private pagingService: ProgrammingExercisePagingService, private sortService: SortService, private activeModal: NgbActiveModal) {}

    ngOnInit() {
        this.content = { resultsOnPage: [], numberOfPages: 0 };

        this.performSearch(this.sort, 0);
        this.performSearch(this.search, 300);
    }

    private performSearch(searchSubject: Subject<void>, debounce: number) {
        searchSubject
            .pipe(
                debounceTime(debounce),
                tap(() => (this.loading = true)),
                switchMap(() => this.pagingService.searchForExercises(this.state, this.isCourseFilter, this.isExamFilter)),
            )
            .subscribe((resp) => {
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

    set listSorting(ascending: boolean) {
        const sortingOrder = ascending ? SortingOrder.ASCENDING : SortingOrder.DESCENDING;
        this.setSearchParam({ sortingOrder });
    }

    get listSorting(): boolean {
        return this.state.sortingOrder === SortingOrder.ASCENDING;
    }

    set sortedColumn(sortedColumn: string) {
        this.setSearchParam({ sortedColumn });
    }

    get sortedColumn(): string {
        return this.state.sortedColumn;
    }

    private setSearchParam(patch: Partial<PageableSearch>) {
        Object.assign(this.state, patch);
        this.sort.next();
    }

    sortRows() {
        this.sortService.sortByProperty(this.content.resultsOnPage, this.sortedColumn, this.listSorting);
    }

    /**
     * Gives the ID for any programming exercise in the table, so that it can be tracked/identified by ngFor
     *
     * @param index The index of the element in the ngFor
     * @param item The exercise itself
     * @returns The ID of the programming exercise
     */
    trackId(index: number, item: ProgrammingExercise): number {
        return item.id!;
    }

    /**
     * Closes the modal in which the import component is opened by dismissing it
     */
    clear() {
        this.activeModal.dismiss('cancel');
    }

    /**
     * Closes the modal in which the import component is opened. Gives the selected exercise as a result.
     *
     * @param exercise The exercise which was selected by the user for the import.
     */
    openImport(exercise: ProgrammingExercise) {
        this.activeModal.close(exercise);
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
