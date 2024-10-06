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

    private feedbackAnalysisService = inject(FeedbackAnalysisService);
    private alertService = inject(AlertService);
    private modalService = inject(NgbModal);
    private localStorage = inject(LocalStorageService);

    readonly page = signal<number>(1);
    readonly pageSize = signal<number>(10);
    readonly searchTerm = signal<string>('');
    readonly sortingOrder = signal<SortingOrder>(SortingOrder.DESCENDING);
    readonly sortedColumn = signal<string>('count');

    readonly content = signal<SearchResult<FeedbackDetail>>({ resultsOnPage: [], numberOfPages: 0 });
    readonly totalItems = signal<number>(0);
    readonly collectionsSize = computed(() => this.content().numberOfPages * this.pageSize());

    readonly faSort = faSort;
    readonly faSortUp = faSortUp;
    readonly faSortDown = faSortDown;
    readonly faFilter = faFilter;
    readonly faUpRightAndDownLeftFromCenter = faUpRightAndDownLeftFromCenter;
    readonly SortingOrder = SortingOrder;
    readonly MAX_FEEDBACK_DETAIL_TEXT_LENGTH = 150;

    readonly sortIcon = computed(() => (this.sortingOrder() === SortingOrder.ASCENDING ? this.faSortUp : this.faSortDown));

    private hasAppliedFilters: boolean = false;
    readonly FILTER_TASKS_KEY = 'feedbackAnalysis.tasks';
    readonly FILTER_TEST_CASES_KEY = 'feedbackAnalysis.testCases';
    readonly FILTER_OCCURRENCE_KEY = 'feedbackAnalysis.occurrence';
    selectedFiltersCount = signal<number>(0);
    totalAmountOfTasks = signal<number>(0);
    testCaseNames = signal<string[]>([]);

    constructor() {
        effect(() => {
            untracked(async () => {
                await this.loadData();
            });
        });
    }

    private async loadData(): Promise<void> {
        const savedTasks = this.localStorage.retrieve(this.FILTER_TASKS_KEY) || [];
        const savedTestCases = this.localStorage.retrieve(this.FILTER_TEST_CASES_KEY) || [];
        const savedOccurrence = this.localStorage.retrieve(this.FILTER_OCCURRENCE_KEY) || [0, 100];

        const state = {
            page: this.page(),
            pageSize: this.pageSize(),
            searchTerm: this.searchTerm() || '',
            sortingOrder: this.sortingOrder(),
            sortedColumn: this.sortedColumn(),
        };

        try {
            const response = await this.feedbackAnalysisService.search(state, {
                exerciseId: this.exerciseId(),
                filters: {
                    tasks: this.hasAppliedFilters ? savedTasks : [],
                    testCases: this.hasAppliedFilters ? savedTestCases : [],
                    occurrence: this.hasAppliedFilters ? savedOccurrence : [],
                },
            });
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

    setSortedColumn(column: string): void {
        if (this.sortedColumn() === column) {
            this.sortingOrder.set(this.sortingOrder() === SortingOrder.ASCENDING ? SortingOrder.DESCENDING : SortingOrder.ASCENDING);
        } else {
            this.sortedColumn.set(column);
            this.sortingOrder.set(SortingOrder.ASCENDING);
        }
        this.loadData();
    }

    openFilterModal(): void {
        const savedTasks = this.localStorage.retrieve(this.FILTER_TASKS_KEY) || [];
        const savedTestCases = this.localStorage.retrieve(this.FILTER_TEST_CASES_KEY) || [];
        const savedOccurrence = this.localStorage.retrieve(this.FILTER_OCCURRENCE_KEY) || [0, 100];

        const modalRef = this.modalService.open(FeedbackFilterModalComponent, { centered: true, size: 'lg' });

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
        this.selectedFiltersCount.set(this.countAppliedFilters(filters));
        this.hasAppliedFilters = this.selectedFiltersCount() !== 0;

        this.localStorage.store(this.FILTER_TASKS_KEY, filters.tasks);
        this.localStorage.store(this.FILTER_TEST_CASES_KEY, filters.testCases);
        this.localStorage.store(this.FILTER_OCCURRENCE_KEY, filters.occurrence);

        this.loadData();
    }

    countAppliedFilters(filters: any): number {
        let count = 0;
        if (filters.tasks && filters.tasks.length > 0) {
            count += filters.tasks.length;
        }
        if (filters.testCases && filters.testCases.length > 0) {
            count += filters.testCases.length;
        }
        if (filters.occurrence && (filters.occurrence[0] !== 0 || filters.occurrence[1] !== 100)) {
            count++;
        }
        return count;
    }
}
