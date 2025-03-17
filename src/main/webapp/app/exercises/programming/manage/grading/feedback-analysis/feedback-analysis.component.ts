import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FeedbackAnalysisResponse, FeedbackAnalysisService, FeedbackChannelRequestDTO, FeedbackDetail } from './feedback-analysis.service';
import { NgbModal, NgbModule, NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/shared/service/alert.service';
import { faCircleQuestion, faFilter, faMessage, faSort, faSpinner, faUsers } from '@fortawesome/free-solid-svg-icons';
import { SearchResult, SortingOrder } from 'app/shared/table/pageable-table';
import { FeedbackModalComponent } from 'app/exercises/programming/manage/grading/feedback-analysis/modal/feedback-modal.component';
import { FeedbackFilterModalComponent, FilterData } from 'app/exercises/programming/manage/grading/feedback-analysis/modal/feedback-filter-modal.component';
import { LocalStorageService } from 'ngx-webstorage';
import { BaseApiHttpService } from 'app/shared/service/base-api-http.service';
import { SortIconComponent } from 'app/shared/sort/sort-icon.component';
import { AffectedStudentsModalComponent } from 'app/exercises/programming/manage/grading/feedback-analysis/modal/feedback-affected-students-modal.component';
import { FeedbackDetailChannelModalComponent } from 'app/exercises/programming/manage/grading/feedback-analysis/modal/feedback-detail-channel-modal.component';
import { ChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { Router } from '@angular/router';
import { facDetails } from 'app/icons/icons';
import dayjs from 'dayjs/esm';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { CommonModule } from '@angular/common';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FormsModule } from '@angular/forms';

export interface FeedbackAnalysisState {
    page: number;
    pageSize: number;
    searchTerm: string;
    sortingOrder: SortingOrder;
    sortedColumn: string;
    filterErrorCategories: string[];
}
@Component({
    selector: 'jhi-feedback-analysis',
    templateUrl: './feedback-analysis.component.html',
    styleUrls: ['./feedback-analysis.component.scss'],
    imports: [SortIconComponent, NgbModule, NgbPagination, TranslateDirective, FontAwesomeModule, CommonModule, ArtemisTranslatePipe, FormsModule],
    providers: [FeedbackAnalysisService],
})
export class FeedbackAnalysisComponent {
    exerciseTitle = input.required<string>();
    exerciseId = input.required<number>();
    exerciseDueDate = input<dayjs.Dayjs | undefined>();
    courseId = input.required<number>();
    isCommunicationEnabled = input.required<boolean>();

    private feedbackAnalysisService = inject(FeedbackAnalysisService);
    private alertService = inject(AlertService);
    private modalService = inject(NgbModal);
    private localStorage = inject(LocalStorageService);
    private router = inject(Router);

    readonly page = signal<number>(1);
    readonly pageSize = signal<number>(25);
    readonly searchTerm = signal<string>('');
    readonly sortingOrder = signal<SortingOrder>(SortingOrder.DESCENDING);
    readonly sortedColumn = signal<string>('count');

    readonly content = signal<SearchResult<FeedbackDetail>>({ resultsOnPage: [], numberOfPages: 0 });
    readonly totalItems = signal<number>(0);
    readonly collectionsSize = computed(() => this.content().numberOfPages * this.pageSize());

    readonly TRANSLATION_BASE = 'artemisApp.programmingExercise.configureGrading.feedbackAnalysis';
    readonly faSort = faSort;
    readonly faFilter = faFilter;
    readonly facDetails = facDetails;
    readonly faUsers = faUsers;
    readonly faMessage = faMessage;
    readonly faCircleQuestion = faCircleQuestion;
    readonly SortingOrder = SortingOrder;
    readonly MAX_FEEDBACK_DETAIL_TEXT_LENGTH = 200;
    readonly faSpinner = faSpinner;
    readonly isLoading = signal<boolean>(false);

    readonly FILTER_TASKS_KEY = 'feedbackAnalysis.tasks';
    readonly FILTER_TEST_CASES_KEY = 'feedbackAnalysis.testCases';
    readonly FILTER_OCCURRENCE_KEY = 'feedbackAnalysis.occurrence';
    readonly FILTER_ERROR_CATEGORIES_KEY = 'feedbackAnalysis.errorCategories';
    readonly selectedFiltersCount = signal<number>(0);
    readonly taskNames = signal<string[]>([]);
    readonly testCaseNames = signal<string[]>([]);
    readonly minCount = signal<number>(0);
    readonly maxCount = signal<number>(0);
    readonly errorCategories = signal<string[]>(['Student Error', 'Ares Error', 'AST Error']);

    private isFeedbackDetailChannelModalOpen = false;

    private readonly debounceLoadData = BaseApiHttpService.debounce(this.loadData.bind(this), 300);
    readonly groupFeedback = signal<boolean>(false);

    currentRequestFilters = signal<FilterData | undefined>(undefined);
    currentRequestState = signal<FeedbackAnalysisState | undefined>(undefined);
    currentRequestGroupFeedback = signal<boolean | undefined>(undefined);
    currentResponseData = signal<FeedbackAnalysisResponse | undefined>(undefined);

    previousRequestFilters = signal<FilterData | undefined>(undefined);
    previousRequestState = signal<FeedbackAnalysisState | undefined>(undefined);
    previousRequestGroupFeedback = signal<boolean | undefined>(undefined);
    previousResponseData = signal<FeedbackAnalysisResponse | undefined>(undefined);

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
        const savedOccurrence = this.localStorage.retrieve(this.FILTER_OCCURRENCE_KEY) || [];
        const savedErrorCategories = this.localStorage.retrieve(this.FILTER_ERROR_CATEGORIES_KEY) || [];

        const state = {
            page: this.page(),
            pageSize: this.pageSize(),
            searchTerm: this.searchTerm() || '',
            sortingOrder: this.sortingOrder(),
            sortedColumn: this.sortedColumn(),
            filterErrorCategories: this.errorCategories(),
        };

        const filters = {
            tasks: this.selectedFiltersCount() !== 0 ? savedTasks : [],
            testCases: this.selectedFiltersCount() !== 0 ? savedTestCases : [],
            occurrence: this.selectedFiltersCount() !== 0 ? savedOccurrence : [],
            errorCategories: this.selectedFiltersCount() !== 0 ? savedErrorCategories : [],
        };

        // cache related check if loading new data is necessary
        const previousResponseData: FeedbackAnalysisResponse | undefined = this.previousResponseData();
        if (
            JSON.stringify(this.previousRequestFilters()) === JSON.stringify(filters) &&
            JSON.stringify(this.previousRequestState()) === JSON.stringify(state) &&
            this.previousRequestGroupFeedback() === this.groupFeedback() &&
            previousResponseData
        ) {
            this.updateCache(previousResponseData, state, filters);
            return;
        }

        this.isLoading.set(true);
        try {
            const response = await this.feedbackAnalysisService.search(state, this.groupFeedback(), {
                exerciseId: this.exerciseId(),
                filters,
            });
            this.updateCache(response, state, filters);
        } catch (error) {
            this.alertService.error(this.TRANSLATION_BASE + '.error');
        } finally {
            this.isLoading.set(false);
        }
    }

    updateCache(response: FeedbackAnalysisResponse, state: FeedbackAnalysisState, filters: FilterData): void {
        this.content.set(response.feedbackDetails);
        this.totalItems.set(response.totalItems);
        this.taskNames.set(response.taskNames);
        this.testCaseNames.set(response.testCaseNames);
        this.errorCategories.set(response.errorCategories);
        this.maxCount.set(response.highestOccurrenceOfGroupedFeedback);

        this.previousResponseData.set(this.currentResponseData());
        this.previousRequestGroupFeedback.set(this.currentRequestGroupFeedback());
        this.previousRequestState.set(this.currentRequestState());
        this.previousRequestFilters.set(this.currentRequestFilters());

        this.currentResponseData.set(response);
        this.currentRequestGroupFeedback.set(this.groupFeedback());
        this.currentRequestState.set(state);
        this.currentRequestFilters.set(filters);
    }

    setPage(newPage: number): void {
        this.page.set(newPage);
        this.loadData();
    }

    async search(searchTerm: string): Promise<void> {
        this.page.set(1);
        this.searchTerm.set(searchTerm);
        this.debounceLoadData();
    }

    openFeedbackModal(feedbackDetail: FeedbackDetail): void {
        const modalRef = this.modalService.open(FeedbackModalComponent, { centered: true, size: 'lg' });
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

    getSortDirection(column: string): SortingOrder.ASCENDING | SortingOrder.DESCENDING | 'none' {
        if (this.sortedColumn() === column) {
            return this.sortingOrder() === SortingOrder.ASCENDING ? SortingOrder.ASCENDING : SortingOrder.DESCENDING;
        }
        return 'none';
    }

    async openFilterModal(): Promise<void> {
        const savedTasks = this.localStorage.retrieve(this.FILTER_TASKS_KEY);
        const savedTestCases = this.localStorage.retrieve(this.FILTER_TEST_CASES_KEY);
        const savedOccurrence = this.localStorage.retrieve(this.FILTER_OCCURRENCE_KEY);
        const savedErrorCategories = this.localStorage.retrieve(this.FILTER_ERROR_CATEGORIES_KEY);
        this.minCount.set(0);
        if (this.groupFeedback()) {
            this.maxCount.set(this.maxCount());
        } else {
            this.maxCount.set(await this.feedbackAnalysisService.getMaxCount(this.exerciseId()));
        }

        const modalRef = this.modalService.open(FeedbackFilterModalComponent, { centered: true, size: 'lg' });

        modalRef.componentInstance.exerciseId = this.exerciseId;
        modalRef.componentInstance.taskArray = this.taskNames;
        modalRef.componentInstance.testCaseNames = this.testCaseNames;
        modalRef.componentInstance.maxCount = this.maxCount;
        modalRef.componentInstance.errorCategories = this.errorCategories;
        modalRef.componentInstance.filters = {
            tasks: this.selectedFiltersCount() !== 0 ? savedTasks : [],
            testCases: this.selectedFiltersCount() !== 0 ? savedTestCases : [],
            occurrence: this.selectedFiltersCount() !== 0 ? savedOccurrence : [this.minCount(), this.maxCount()],
            errorCategories: this.selectedFiltersCount() !== 0 ? savedErrorCategories : [],
        };
        modalRef.componentInstance.filterApplied.subscribe((filters: any) => {
            this.applyFilters(filters);
        });
    }

    applyFilters(filters: FilterData): void {
        this.selectedFiltersCount.set(this.countAppliedFilters(filters));
        this.loadData();
    }

    countAppliedFilters(filters: FilterData): number {
        let count = 0;
        if (filters.tasks && filters.tasks.length > 0) {
            count += filters.tasks.length;
        }
        if (filters.testCases && filters.testCases.length > 0) {
            count += filters.testCases.length;
        }
        if (filters.errorCategories?.length) {
            count += filters.errorCategories.length;
        }
        if (filters.occurrence && (filters.occurrence[0] !== 0 || filters.occurrence[1] !== this.maxCount())) {
            count++;
        }
        return count;
    }

    async openAffectedStudentsModal(feedbackDetail: FeedbackDetail): Promise<void> {
        const modalRef = this.modalService.open(AffectedStudentsModalComponent, { centered: true, size: 'lg' });
        modalRef.componentInstance.courseId = this.courseId;
        modalRef.componentInstance.exerciseId = this.exerciseId;
        modalRef.componentInstance.feedbackDetail = signal(feedbackDetail);
    }

    async openFeedbackDetailChannelModal(feedbackDetail: FeedbackDetail): Promise<void> {
        if (this.isFeedbackDetailChannelModalOpen) {
            return;
        }
        this.isFeedbackDetailChannelModalOpen = true;
        const modalRef = this.modalService.open(FeedbackDetailChannelModalComponent, { centered: true, size: 'lg' });
        modalRef.componentInstance.feedbackDetail = signal(feedbackDetail);
        modalRef.componentInstance.exerciseDueDate = signal(this.exerciseDueDate());
        modalRef.componentInstance.formSubmitted.subscribe(async ({ channelDto, navigate }: { channelDto: ChannelDTO; navigate: boolean }) => {
            try {
                const feedbackChannelRequest: FeedbackChannelRequestDTO = {
                    channel: channelDto,
                    feedbackDetailTexts: feedbackDetail.detailTexts,
                    testCaseName: feedbackDetail.testCaseName,
                };
                const createdChannel = await this.feedbackAnalysisService.createChannel(this.courseId(), this.exerciseId(), feedbackChannelRequest);
                const channelName = createdChannel.name;
                this.alertService.success(this.TRANSLATION_BASE + '.channelSuccess', { channelName });
                if (navigate) {
                    const urlTree = this.router.createUrlTree(['courses', this.courseId(), 'communication'], {
                        queryParams: { conversationId: createdChannel.id },
                    });
                    await this.router.navigateByUrl(urlTree);
                }
            } catch (error) {
                this.alertService.error(error);
            }
        });
        try {
            await modalRef.result;
        } catch {
            // modal dismissed
        } finally {
            this.isFeedbackDetailChannelModalOpen = false;
        }
    }

    toggleGroupFeedback(): void {
        this.groupFeedback.update((current) => !current);
        this.loadData();
    }
}
