import { Component, InputSignal, computed, effect, inject, input, signal } from '@angular/core';
import { FeedbackAnalysisService, FeedbackDetail } from 'app/exercises/programming/manage/grading/feedback-analysis/feedback-analysis.service';
import { AlertService } from 'app/core/util/alert.service';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { faMagnifyingGlass, faMagnifyingGlassPlus, faSort, faSortDown, faSortUp } from '@fortawesome/free-solid-svg-icons';
import { FeedbackModalComponent } from 'app/exercises/programming/manage/grading/feedback-analysis/Modal/feedback-modal.component';
import { SortService } from 'app/shared/service/sort.service';

enum SortingOrder {
    ASCENDING = 'ASC',
    DESCENDING = 'DESC',
}

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

    private feedbackAnalysisService = inject(FeedbackAnalysisService);
    private alertService = inject(AlertService);
    private modalService = inject(NgbModal);
    private sortService = inject(SortService);

    readonly feedbackDetails = signal<FeedbackDetail[]>([]);

    readonly sortedColumn = signal<string>('count');
    readonly sortingOrder = signal<SortingOrder>(SortingOrder.DESCENDING);
    readonly faSort = faSort;
    readonly faSortUp = faSortUp;
    readonly faSortDown = faSortDown;
    readonly faMagnifyingGlass = faMagnifyingGlass;
    readonly faMagnifyingGlassPlus = faMagnifyingGlassPlus;

    readonly page = signal<number>(1);
    readonly pageSize = signal<number>(15);
    readonly searchTerm = signal<string>('');

    readonly paginatedFeedbackDetails = computed(() => {
        const filteredAndSorted = this.getFilteredAndSortedFeedback();
        const start = (this.page() - 1) * this.pageSize();
        const end = start + this.pageSize();
        return filteredAndSorted.slice(start, end);
    });

    readonly collectionSize = computed(() => this.getFilteredAndSortedFeedback().length);

    constructor() {
        effect(() => {
            this.loadFeedbackDetails(this.exerciseId());
        });
    }

    async loadFeedbackDetails(exerciseId: number): Promise<void> {
        try {
            this.feedbackDetails.set(await this.feedbackAnalysisService.getFeedbackDetailsForExercise(exerciseId));
        } catch (error) {
            this.alertService.error(`artemisApp.programmingExercise.configureGrading.feedbackAnalysis.error`);
        }
    }

    private getFilteredAndSortedFeedback() {
        const searchTermLower = this.searchTerm().toLowerCase();
        const searchTermNumber = Number(this.searchTerm());
        const hasNumber = !isNaN(searchTermNumber);

        const filtered = this.filterForSearch(searchTermLower, hasNumber, searchTermNumber);
        return this.sortFeedbackDetails(filtered);
    }

    private sortFeedbackDetails(details: FeedbackDetail[]): FeedbackDetail[] {
        const column = this.sortedColumn();
        const order = this.sortingOrder() === SortingOrder.ASCENDING;
        return this.sortService.sortByProperty(details, column, order);
    }

    private filterForSearch(searchTermLower: string, hasNumber: boolean, searchTermNumber: number) {
        return this.feedbackDetails().filter((item) => {
            const matchesTextFields = item.detailText.toLowerCase().includes(searchTermLower) || item.testCaseName.toLowerCase().includes(searchTermLower);

            const matchesNumericFields = hasNumber && (item.taskNumber === searchTermNumber || item.count === searchTermNumber || item.relativeCount === searchTermNumber);

            return matchesTextFields || matchesNumericFields;
        });
    }

    setPage(newPage: number): void {
        this.page.set(newPage);
    }

    search(): void {
        this.page.set(1);
    }

    setSortedColumn(column: string): void {
        if (this.sortedColumn() === column) {
            this.sortingOrder.set(this.sortingOrder() === SortingOrder.ASCENDING ? SortingOrder.DESCENDING : SortingOrder.ASCENDING);
        } else {
            this.sortedColumn.set(column);
            this.sortingOrder.set(SortingOrder.ASCENDING);
        }
        this.page.set(1);
    }

    openFeedbackModal(feedbackDetail: FeedbackDetail): void {
        const modalRef = this.modalService.open(FeedbackModalComponent, { centered: true });
        modalRef.componentInstance.feedbackDetail = feedbackDetail;
    }
}
