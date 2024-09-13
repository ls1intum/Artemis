import { Component, InputSignal, effect, inject, input, signal } from '@angular/core';
import { FeedbackAnalysisResponse, FeedbackAnalysisService, FeedbackDetail } from './feedback-analysis.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/core/util/alert.service';
import { faMagnifyingGlass, faMagnifyingGlassPlus, faSort, faSortDown, faSortUp } from '@fortawesome/free-solid-svg-icons';
import { SearchResult, SortingOrder } from 'app/shared/table/pageable-table';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { FeedbackModalComponent } from 'app/exercises/programming/manage/grading/feedback-analysis/Modal/feedback-modal.component';

@Component({
    selector: 'jhi-feedback-analysis',
    templateUrl: './feedback-analysis.component.html',
    styleUrls: ['./feedback-analysis.component.scss'],
    standalone: true,
    imports: [ArtemisSharedCommonModule],
    providers: [FeedbackAnalysisService],
})
export class FeedbackAnalysisComponent {
    exerciseTitle: InputSignal<string> = input.required<string>();
    exerciseId: InputSignal<number> = input.required<number>();

    // Signals for reactive state
    readonly page = signal<number>(1);
    readonly pageSize = signal<number>(15);
    searchTerm = signal<string>(''); // Initially empty
    readonly sortingOrder = signal<SortingOrder>(SortingOrder.DESCENDING);
    readonly sortedColumn = signal<string>('count');

    readonly isLoading = signal<boolean>(false);
    readonly content = signal<SearchResult<FeedbackDetail>>({ resultsOnPage: [], numberOfPages: 0 });
    distinctResultCount = signal<number>(0); // To store the distinct result count

    // Inject dependencies
    private pagingService = inject(FeedbackAnalysisService);
    private alertService = inject(AlertService);
    private modalService = inject(NgbModal);

    readonly faSort = faSort;
    readonly faSortUp = faSortUp;
    readonly faSortDown = faSortDown;
    readonly faMagnifyingGlass = faMagnifyingGlass;
    readonly faMagnifyingGlassPlus = faMagnifyingGlassPlus;

    constructor() {
        effect(() => {
            this.loadData(); // This will be triggered immediately upon page load
        });
    }

    private loadData(): void {
        const state = {
            page: this.page(),
            pageSize: this.pageSize(),
            searchTerm: this.searchTerm(), // Will be empty initially
            sortingOrder: this.sortingOrder(),
            sortedColumn: this.sortedColumn(),
        };

        this.pagingService
            .search(state, { exerciseId: this.exerciseId() }) // Make sure exerciseId is correct
            .subscribe({
                next: (response: FeedbackAnalysisResponse) => {
                    this.content.set(response.feedbackDetails);
                    this.distinctResultCount.set(response.distinctResultCount); // Store distinct result count
                },
                error: (error) => {
                    this.alertService.error(error.message);
                },
            });
    }

    setPage(newPage: number): void {
        this.page.set(newPage);
        this.loadData();
    }

    setSortedColumn(column: string): void {
        if (this.sortedColumn() === column) {
            this.sortingOrder.set(this.sortingOrder() === SortingOrder.ASCENDING ? SortingOrder.DESCENDING : SortingOrder.ASCENDING);
        } else {
            this.sortedColumn.set(column);
            this.sortingOrder.set(SortingOrder.ASCENDING);
        }
        this.loadData();
    }

    search(): void {
        this.page.set(1); // Reset to page 1 when searching
        this.loadData();
    }

    openFeedbackModal(feedbackDetail: FeedbackDetail): void {
        const modalRef = this.modalService.open(FeedbackModalComponent, { centered: true });
        modalRef.componentInstance.feedbackDetail = feedbackDetail;
    }

    protected readonly SortingOrder = SortingOrder;
}
