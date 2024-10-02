import { Component, InputSignal, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FeedbackAnalysisService, FeedbackDetail } from './feedback-analysis.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/core/util/alert.service';
import { faFilter, faSort, faSortDown, faSortUp, faUpRightAndDownLeftFromCenter } from '@fortawesome/free-solid-svg-icons';
import { SearchResult, SortingOrder } from 'app/shared/table/pageable-table';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { FeedbackModalComponent } from 'app/exercises/programming/manage/grading/feedback-analysis/Modal/feedback-modal.component';
import { FeedbackFilterModalComponent } from 'app/exercises/programming/manage/grading/feedback-analysis/Modal/feedback-filter-modal.component';
import { LocalStorageService } from 'ngx-webstorage';

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
    totalAmountOfTasks = signal<number>(0);
    testCaseNames = signal<string[]>([]);

    readonly page = signal<number>(1);
    readonly pageSize = signal<number>(20);
    readonly searchTerm = signal<string>('');
    readonly sortingOrder = signal<SortingOrder>(SortingOrder.DESCENDING);
    readonly sortedColumn = signal<string>('count');

    readonly content = signal<SearchResult<FeedbackDetail>>({ resultsOnPage: [], numberOfPages: 0 });
    readonly totalItems = signal<number>(0);
    readonly collectionsSize = computed(() => this.content().numberOfPages * this.pageSize());

    private feedbackAnalysisService = inject(FeedbackAnalysisService);
    private alertService = inject(AlertService);
    private modalService = inject(NgbModal);

    readonly faSort = faSort;
    readonly faSortUp = faSortUp; // Added for sorting icons
    readonly faSortDown = faSortDown; // Added for sorting icons
    readonly faFilter = faFilter;
    readonly faUpRightAndDownLeftFromCenter = faUpRightAndDownLeftFromCenter;
    readonly SortingOrder = SortingOrder;
    readonly MAX_FEEDBACK_DETAIL_TEXT_LENGTH = 150;

    // Added sortIcon computed function to dynamically set the sort icon
    readonly sortIcon = computed(() => (this.sortingOrder() === SortingOrder.ASCENDING ? this.faSortUp : this.faSortDown));

    private localStorage = inject(LocalStorageService);
    selectedFiltersCount = 0;
    private hasAppliedFilters: boolean = false;
    readonly FILTER_TASKS_KEY = 'feedbackAnalysis.tasks';
    readonly FILTER_TEST_CASES_KEY = 'feedbackAnalysis.testCases';
    readonly FILTER_OCCURRENCE_KEY = 'feedbackAnalysis.occurrence';

    constructor() {
        effect(() => {
            untracked(async () => {
                await this.loadData();
            });
        });
    }

    // Set sorted column and toggle sorting order when the column header is clicked
    setSortedColumn(column: string): void {
        // NEW FUNCTION
        if (this.sortedColumn() === column) {
            // Toggle the sorting order if the same column is clicked
            this.sortingOrder.set(this.sortingOrder() === SortingOrder.ASCENDING ? SortingOrder.DESCENDING : SortingOrder.ASCENDING);
        } else {
            // Set a new column and default sorting order to ascending
            this.sortedColumn.set(column);
            this.sortingOrder.set(SortingOrder.ASCENDING);
        }
        this.loadData(); // Reload data with the new sorting configuration
    }

    async openFilterModal(): Promise<void> {
        const savedTasks = this.localStorage.retrieve(this.FILTER_TASKS_KEY) || [];
        const savedTestCases = this.localStorage.retrieve(this.FILTER_TEST_CASES_KEY) || [];
        const savedOccurrence = this.localStorage.retrieve(this.FILTER_OCCURRENCE_KEY) || [0, 100];

        const modalRef = this.modalService.open(FeedbackFilterModalComponent, { centered: true });

        modalRef.componentInstance.filterForm.setValue({
            tasks: this.hasAppliedFilters ? savedTasks : [],
            testCases: this.hasAppliedFilters ? savedTestCases : [],
            occurrence: this.hasAppliedFilters ? savedOccurrence : [0, 100],
        });

        modalRef.componentInstance.totalAmountOfTasks = this.totalAmountOfTasks;
        modalRef.componentInstance.testCaseNames = this.testCaseNames;
        modalRef.componentInstance.filterApplied.subscribe((filters: any) => {
            this.applyFilters(filters);
        });
    }

    applyFilters(filters: any): void {
        this.selectedFiltersCount = this.countAppliedFilters(filters);
        this.hasAppliedFilters = this.selectedFiltersCount !== 0;
        this.loadData();
    }

    countAppliedFilters(filters: any): number {
        let count = 0;
        // Count the number of tasks selected
        if (filters.tasks && filters.tasks.length > 0) {
            count += filters.tasks.length; // Add the length of tasks array to the count
        }
        // Count the number of test cases selected
        if (filters.testCases && filters.testCases.length > 0) {
            count += filters.testCases.length; // Add the length of testCases array to the count
        }
        // Add 1 if occurrence filter is applied (range is not [0, 100])
        if (filters.occurrence && (filters.occurrence[0] !== 0 || filters.occurrence[1] !== 100)) {
            count++; // Occurrence is still a single filter, so count as 1
        }
        return count;
    }

    private async loadData(): Promise<void> {
        const state = {
            page: this.page(),
            pageSize: this.pageSize(),
            searchTerm: this.searchTerm() || '',
            sortingOrder: this.sortingOrder(),
            sortedColumn: this.sortedColumn(),
        };

        try {
            const response = await this.feedbackAnalysisService.search(state, { exerciseId: this.exerciseId() });
            this.content.set(response.feedbackDetails);
            this.totalItems.set(response.totalItems);
            this.totalAmountOfTasks.set(response.totalAmountOfTasks);
            this.testCaseNames.set(response.testCaseNames);
        } catch (error) {
            this.alertService.error('artemisApp.programmingExercise.configureGrading.feedbackAnalysis.error');
        }
    }

    setPage(newPage: number): void {
        this.page.set(newPage);
        this.loadData();
    }

    async search(searchTerm: string): Promise<void> {
        this.page.set(1);
        this.searchTerm.set(searchTerm);
        await this.loadData();
    }

    openFeedbackModal(feedbackDetail: FeedbackDetail): void {
        const modalRef = this.modalService.open(FeedbackModalComponent, { centered: true });
        modalRef.componentInstance.feedbackDetail = signal(feedbackDetail);
    }
}
