import { Component, OnInit } from '@angular/core';
import { debounceTime, switchMap, tap } from 'rxjs/operators';
import { Subject } from 'rxjs';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { PageableSearch, SearchResult, SortingOrder } from 'app/shared/table/pageable-table';
import { SortService } from 'app/shared/service/sort.service';
import { Router } from '@angular/router';
import { faCheck, faSort } from '@fortawesome/free-solid-svg-icons';
import { LecturePagingService } from 'app/lecture/lecture-paging.service';
import { Lecture } from 'app/entities/lecture.model';

export enum TableColumn {
    ID = 'ID',
    TITLE = 'TITLE',
    COURSE_TITLE = 'COURSE_TITLE',
    SEMESTER = 'SEMESTER',
}

@Component({
    selector: 'jhi-lecture-import',
    templateUrl: './lecture-import.component.html',
})
export class LectureImportComponent implements OnInit {
    readonly column = TableColumn;
    loading = false;
    content: SearchResult<Lecture>;
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

    constructor(private router: Router, private pagingService: LecturePagingService, private sortService: SortService, private activeModal: NgbActiveModal) {}

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
     * Gives the ID for any lecture in the table, so that it can be tracked/identified by ngFor
     *
     * @param index The index of the element in the ngFor
     * @param item The lecture itself
     * @returns The ID of the lecture
     */
    trackId(index: number, item: Lecture): number {
        return item.id!;
    }

    /**
     * Closes the modal in which the import component is opened. Gives the selected lecture as a result.
     *
     * @param lecture The lecture which was selected by the user for the import.
     */
    selectImport(lecture: Lecture) {
        this.activeModal.close(lecture);
    }

    openLectureInNewTab(lecture: Lecture) {
        const url = this.router.serializeUrl(this.router.createUrlTree(['course-management', lecture.course!.id, 'lectures', lecture.id]));
        window.open(url, '_blank');
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
     * @param debounce The delay we apply to delay the feedback / wait for input
     */
    private performSearch(searchSubject: Subject<void>, debounce: number) {
        searchSubject
            .pipe(
                debounceTime(debounce),
                tap(() => (this.loading = true)),
                switchMap(() => this.pagingService.searchForLectures(this.state)),
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
