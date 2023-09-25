import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subject } from 'rxjs';
import { LearningPathService } from 'app/course/learning-paths/learning-path.service';
import { debounceTime, finalize, switchMap, tap } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { PageableSearch, SearchResult, SortingOrder } from 'app/shared/table/pageable-table';
import { LearningPathPagingService } from 'app/course/learning-paths/learning-path-paging.service';
import { SortService } from 'app/shared/service/sort.service';
import { LearningPathPageableSearchDTO } from 'app/entities/competency/learning-path.model';
import { faSort, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import { HealthStatus, LearningPathHealthDTO } from 'app/entities/competency/learning-path-health.model';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { LearningPathProgressModalComponent } from 'app/course/learning-paths/learning-path-management/learning-path-progress-modal.component';

export enum TableColumn {
    ID = 'ID',
    USER_NAME = 'USER_NAME',
    USER_LOGIN = 'USER_LOGIN',
    PROGRESS = 'PROGRESS',
}

@Component({
    selector: 'jhi-learning-path-management',
    templateUrl: './learning-path-management.component.html',
})
export class LearningPathManagementComponent implements OnInit {
    isLoading = false;

    courseId: number;
    health: LearningPathHealthDTO;

    searchLoading = false;
    readonly column = TableColumn;
    state: PageableSearch = {
        page: 1,
        pageSize: 50,
        searchTerm: '',
        sortingOrder: SortingOrder.ASCENDING,
        sortedColumn: TableColumn.ID,
    };
    content: SearchResult<LearningPathPageableSearchDTO>;
    total = 0;

    private search = new Subject<void>();
    private sort = new Subject<void>();

    // icons
    faSort = faSort;
    faTriangleExclamation = faTriangleExclamation;

    constructor(
        private activatedRoute: ActivatedRoute,
        private learningPathService: LearningPathService,
        private alertService: AlertService,
        private pagingService: LearningPathPagingService,
        private sortService: SortService,
        private modalService: NgbModal,
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

    /**
     * Gives the ID for any item in the table, so that it can be tracked/identified by ngFor
     *
     * @param index The index of the element in the ngFor
     * @param item The item itself
     * @returns The ID of the item
     */
    trackId(index: number, item: LearningPathPageableSearchDTO): number {
        return item.id!;
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

        this.activatedRoute.parent!.params.subscribe((params) => {
            this.courseId = params['courseId'];
            if (this.courseId) {
                this.loadData();
            }
        });
    }

    private loadData() {
        this.isLoading = true;

        this.learningPathService
            .getHealthStatusForCourse(this.courseId)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: (res) => {
                    this.health = res.body!;
                    if (this.health.status !== HealthStatus.DISABLED) {
                        this.performSearch(this.sort, 0);
                        this.performSearch(this.search, 300);
                    }
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    enableLearningPaths() {
        this.isLoading = true;
        this.learningPathService.enableLearningPaths(this.courseId).subscribe({
            next: () => {
                this.loadData();
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    generateMissing() {
        this.isLoading = true;
        this.learningPathService.generateMissingLearningPathsForCourse(this.courseId).subscribe({
            next: () => {
                this.loadData();
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    /**
     * Method to perform the search based on a search subject
     *
     * @param searchSubject The search subject which we use to search.
     * @param debounce The delay we apply to delay the feedback / wait for input
     */
    performSearch(searchSubject: Subject<void>, debounce: number): void {
        searchSubject
            .pipe(
                debounceTime(debounce),
                tap(() => (this.searchLoading = true)),
                switchMap(() => this.pagingService.searchForLearningPaths(this.state, this.courseId)),
            )
            .subscribe((resp) => {
                this.content = resp;
                this.searchLoading = false;
                this.total = resp.numberOfPages * this.state.pageSize;
            });
    }

    sortRows() {
        this.sortService.sortByProperty(this.content.resultsOnPage, this.sortedColumn, this.listSorting);
    }

    private setSearchParam(patch: Partial<PageableSearch>): void {
        Object.assign(this.state, patch);
        this.sort.next();
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

    viewLearningPath(learningPath: LearningPathPageableSearchDTO) {
        const modalRef = this.modalService.open(LearningPathProgressModalComponent, {
            size: 'xl',
            backdrop: 'static',
            windowClass: 'learning-path-modal',
        });
        modalRef.componentInstance.courseId = this.courseId;
        modalRef.componentInstance.learningPath = learningPath;
    }

    protected readonly HealthStatus = HealthStatus;
}
