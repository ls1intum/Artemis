import { Component, Input, OnInit } from '@angular/core';
import { debounceTime, switchMap, tap } from 'rxjs/operators';
import { Subject } from 'rxjs';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { PageableSearch, SearchResult, SortingOrder } from 'app/shared/table/pageable-table';
import { SortService } from 'app/shared/service/sort.service';
import { Router } from '@angular/router';
import { faCheck, faSort } from '@fortawesome/free-solid-svg-icons';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { LearningGoalPagingService } from 'app/course/learning-goals/learning-goal-paging.service';

export enum TableColumn {
    ID = 'ID',
    TITLE = 'TITLE',
    COURSE_TITLE = 'COURSE_TITLE',
    SEMESTER = 'SEMESTER',
}

@Component({
    selector: 'jhi-prerequisite-import',
    templateUrl: './prerequisite-import.component.html',
})
export class PrerequisiteImportComponent implements OnInit {
    readonly column = TableColumn;
    loading = false;
    content: SearchResult<LearningGoal>;
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

    constructor(private router: Router, private pagingService: LearningGoalPagingService, private sortService: SortService, private activeModal: NgbActiveModal) {}

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
     * Gives the ID for any learning goal in the table, so that it can be tracked/identified by ngFor
     *
     * @param index The index of the element in the ngFor
     * @param item The learning goal itself
     * @returns The ID of the learning goal
     */
    trackId(index: number, item: LearningGoal): number {
        return item.id!;
    }

    /**
     * Closes the modal in which the import component is opened. Gives the selected learning goal as a result.
     *
     * @param learningGoal The learning goal which was selected by the user for the import.
     */
    selectImport(learningGoal: LearningGoal) {
        this.activeModal.close(learningGoal);
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
     * @param pagenumber The current page number
     */
    onPageChange(pagenumber: number) {
        if (pagenumber) {
            this.page = pagenumber;
        }
    }

    /**
     * Method to perform the search based on a search subject
     *
     * @param searchSubject The search subject which we use to search.
     * @param debounce The delay we apply to deley the feedback / wait for input
     */
    private performSearch(searchSubject: Subject<void>, debounce: number) {
        searchSubject
            .pipe(
                debounceTime(debounce),
                tap(() => (this.loading = true)),
                switchMap(() => this.pagingService.searchForLearningGoals(this.state)),
            )
            .subscribe((resp) => {
                this.content = resp;
                this.loading = false;
                this.total = resp.numberOfPages * this.state.pageSize;
            });
    }

    private setSearchParam(patch: Partial<PageableSearch>) {
        Object.assign(this.state, patch);
        this.sort.next();
    }
}
