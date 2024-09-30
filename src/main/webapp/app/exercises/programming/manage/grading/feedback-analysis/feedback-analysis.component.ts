import { Component, InputSignal, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FeedbackAnalysisService, FeedbackDetail } from './feedback-analysis.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/core/util/alert.service';
import { faFilter, faSort, faUpRightAndDownLeftFromCenter } from '@fortawesome/free-solid-svg-icons';
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
    readonly faFilter = faFilter;
    readonly faUpRightAndDownLeftFromCenter = faUpRightAndDownLeftFromCenter;
    readonly SortingOrder = SortingOrder;
    readonly MAX_FEEDBACK_DETAIL_TEXT_LENGTH = 150;

    selectedFiltersCount = 0;
    currentFilters: any = {};

    private localStorage = inject(LocalStorageService);
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

    async openFilterModal(): Promise<void> {
        // Fetch tasks and test cases from the service
        //const tasks = await this.feedbackAnalysisService.getTasks();
        //const testCases = await this.feedbackAnalysisService.getTestCases();

        // Retrieve saved filter values from local storage
        const savedTasks = this.localStorage.retrieve(this.FILTER_TASKS_KEY) || [];
        const savedTestCases = this.localStorage.retrieve(this.FILTER_TEST_CASES_KEY) || [];
        const savedOccurrence = this.localStorage.retrieve(this.FILTER_OCCURRENCE_KEY) || [0, 100];

        // Determine whether to use default values (first-time or cleared filters)
        const hasAppliedFilters = savedTasks.length || savedTestCases.length || savedOccurrence[0] !== 0 || savedOccurrence[1] !== 100;

        const modalRef = this.modalService.open(FeedbackFilterModalComponent, { centered: true });

        modalRef.componentInstance.localStorageService = this.localStorage;

        // Initialize the form values based on applied filters or default values
        modalRef.componentInstance.filterForm.setValue({
            tasks: hasAppliedFilters ? savedTasks : [], // Use empty array if no filters are applied
            testCases: hasAppliedFilters ? savedTestCases : [],
            occurrence: hasAppliedFilters ? savedOccurrence : [0, 100], // Default occurrence range if no filters
        });

        // Subscribe to the applied filters event
        modalRef.componentInstance.filterApplied.subscribe((filters: any) => {
            this.applyFilters(filters);
        });
    }

    applyFilters(filters: any): void {
        // Save applied filters to local storage
        this.localStorage.store(this.FILTER_TASKS_KEY, filters.tasks);
        this.localStorage.store(this.FILTER_TEST_CASES_KEY, filters.testCases);
        this.localStorage.store(this.FILTER_OCCURRENCE_KEY, filters.occurrence);

        // Update the selected filters count
        this.selectedFiltersCount = this.countAppliedFilters(filters);

        // Load data with the applied filters
        this.loadData();
    }

    // Method to count the number of applied filters
    countAppliedFilters(filters: any): number {
        let count = 0;
        if (filters.tasks && filters.tasks.length > 0) count++;
        if (filters.testCases && filters.testCases.length > 0) count++;
        if (filters.occurrence && (filters.occurrence[0] !== 0 || filters.occurrence[1] !== 100)) count++;
        return count;
    }

    private async loadData(): Promise<void> {
        const state = {
            page: this.page(),
            pageSize: this.pageSize(),
            searchTerm: this.searchTerm() || '', // Pass empty string if searchTerm is not set
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
