import { Component, InputSignal, computed, effect, inject, input, signal } from '@angular/core';
import { FeedbackAnalysisService, FeedbackDetail } from './feedback-analysis.service';
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

    readonly page = signal<number>(1);
    readonly pageSize = signal<number>(20);
    searchTerm = signal<string>('');
    readonly sortingOrder = signal<SortingOrder>(SortingOrder.DESCENDING);
    readonly sortedColumn = signal<string>('count');

    readonly content = signal<SearchResult<FeedbackDetail>>({ resultsOnPage: [], numberOfPages: 0 });
    readonly totalItems = signal<number>(0);
    readonly collectionsSize = computed(() => this.content().numberOfPages * this.pageSize());

    private feedbackAnalysisService = inject(FeedbackAnalysisService);
    private alertService = inject(AlertService);
    private modalService = inject(NgbModal);

    readonly faSort = faSort;
    readonly faSortUp = faSortUp;
    readonly faSortDown = faSortDown;
    readonly faMagnifyingGlass = faMagnifyingGlass;
    readonly faMagnifyingGlassPlus = faMagnifyingGlassPlus;
    readonly SortingOrder = SortingOrder;
    readonly maxFeedbackDetailTextLength = 150;
    readonly sortIcon = computed(() => (this.sortingOrder() === SortingOrder.ASCENDING ? this.faSortUp : this.faSortDown));

    constructor() {
        effect(() => {
            this.loadData();
        });
    }

    private async loadData(): Promise<void> {
        const state = {
            page: this.page(),
            pageSize: this.pageSize(),
            searchTerm: this.searchTerm(),
            sortingOrder: this.sortingOrder(),
            sortedColumn: this.sortedColumn(),
        };

        try {
            const response = await this.feedbackAnalysisService.search(state, { exerciseId: this.exerciseId() });
            this.content.set(response.feedbackDetails);
            this.totalItems.set(response.totalItems);
        } catch (error) {
            this.alertService.error('artemisApp.programmingExercise.configureGrading.feedbackAnalysis.error');
        }
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
        this.page.set(1);
        this.loadData();
    }

    openFeedbackModal(feedbackDetail: FeedbackDetail): void {
        const modalRef = this.modalService.open(FeedbackModalComponent, { centered: true });
        modalRef.componentInstance.feedbackDetail = signal(feedbackDetail); // Pass the signal
    }
}
