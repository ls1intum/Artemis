import { Component, OnInit } from '@angular/core';
import { debounceTime, switchMap, tap } from 'rxjs/operators';
import { Subject } from 'rxjs';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { PageableSearch, SearchResult, SortingOrder } from 'app/shared/table/pageable-table';
import { SortService } from 'app/shared/service/sort.service';
import { faCheck, faSort } from '@fortawesome/free-solid-svg-icons';
import { QuizExercisePagingService } from 'app/exercises/quiz/manage/quiz-exercise-paging.service';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';

export enum TableColumn {
    ID = 'ID',
    TITLE = 'TITLE',
    COURSE_TITLE = 'COURSE_TITLE',
}

@Component({
    selector: 'jhi-quiz-exercise-import',
    templateUrl: './quiz-exercise-import.component.html',
})
export class QuizExerciseImportComponent implements OnInit {
    readonly column = TableColumn;

    private search = new Subject<void>();
    private sort = new Subject<void>();

    loading = false;
    content: SearchResult<QuizExercise>;
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

    constructor(private pagingService: QuizExercisePagingService, private sortService: SortService, private activeModal: NgbActiveModal) {}

    ngOnInit(): void {
        this.content = { resultsOnPage: [], numberOfPages: 0 };

        this.performSearch(this.sort, 0);
        this.performSearch(this.search, 300);
    }

    /** Method to perform the search based on a search subject
     *
     * @param searchSubject The search subject which we use to search.
     * @param debounce The delay we apply to deley the feedback / wait for input
     */
    private performSearch(searchSubject: Subject<void>, debounce: number) {
        searchSubject
            .pipe(
                debounceTime(debounce),
                tap(() => (this.loading = true)),
                switchMap(() => this.pagingService.searchForExercises(this.state)),
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

    sortRows() {
        this.sortService.sortByProperty(this.content.resultsOnPage, this.sortedColumn, this.listSorting);
    }

    /**
     * Gives the ID for any quiz exercise in the table, so that it can be tracked/identified by ngFor
     *
     * @param index The index of the elemnt in the ngFor
     * @param item The exercise itself
     * @returns The ID of the quiz exercise
     */
    trackId(index: number, item: QuizExercise): number {
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

    /**
     * Closes the modal in which the import component is opened. Gives the selected exercise as a result.
     *
     * @param exercise The exercise which was selected by the user for the import.
     */
    openImport(exercise: QuizExercise) {
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
     * @param pagenumber The current page number
     */
    onPageChange(pagenumber: number) {
        if (pagenumber) {
            this.page = pagenumber;
        }
    }
}
