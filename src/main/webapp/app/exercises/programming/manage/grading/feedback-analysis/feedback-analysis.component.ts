import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FeedbackAnalysisService, FeedbackChannelRequestDTO, FeedbackDetail } from './feedback-analysis.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/core/util/alert.service';
import { faFilter, faMessage, faSort, faSortDown, faSortUp, faUpRightAndDownLeftFromCenter, faUsers } from '@fortawesome/free-solid-svg-icons';
import { SearchResult, SortingOrder } from 'app/shared/table/pageable-table';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { FeedbackModalComponent } from 'app/exercises/programming/manage/grading/feedback-analysis/Modal/feedback-modal.component';
import { FeedbackFilterModalComponent, FilterData } from 'app/exercises/programming/manage/grading/feedback-analysis/Modal/feedback-filter-modal.component';
import { LocalStorageService } from 'ngx-webstorage';
import { BaseApiHttpService } from 'app/course/learning-paths/services/base-api-http.service';
import { SortIconComponent } from 'app/shared/sort/sort-icon.component';
import { AffectedStudentsModalComponent } from 'app/exercises/programming/manage/grading/feedback-analysis/Modal/feedback-affected-students-modal.component';
import { FeedbackDetailChannelModalComponent } from 'app/exercises/programming/manage/grading/feedback-analysis/Modal/feedback-detail-channel-modal.component';
import { ChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { Router } from '@angular/router';

@Component({
    selector: 'jhi-feedback-analysis',
    templateUrl: './feedback-analysis.component.html',
    styleUrls: ['./feedback-analysis.component.scss'],
    standalone: true,
    imports: [ArtemisSharedCommonModule, SortIconComponent],
    providers: [FeedbackAnalysisService],
})
export class FeedbackAnalysisComponent {
    exerciseTitle = input.required<string>();
    exerciseId = input.required<number>();
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
    readonly faSortUp = faSortUp;
    readonly faSortDown = faSortDown;
    readonly faFilter = faFilter;
    readonly faUpRightAndDownLeftFromCenter = faUpRightAndDownLeftFromCenter;
    readonly faUsers = faUsers;
    readonly faMessage = faMessage;
    readonly SortingOrder = SortingOrder;
    readonly MAX_FEEDBACK_DETAIL_TEXT_LENGTH = 200;

    readonly FILTER_TASKS_KEY = 'feedbackAnalysis.tasks';
    readonly FILTER_TEST_CASES_KEY = 'feedbackAnalysis.testCases';
    readonly FILTER_OCCURRENCE_KEY = 'feedbackAnalysis.occurrence';
    readonly FILTER_ERROR_CATEGORIES_KEY = 'feedbackAnalysis.errorCategories';
    readonly selectedFiltersCount = signal<number>(0);
    readonly taskNames = signal<string[]>([]);
    readonly testCaseNames = signal<string[]>([]);
    readonly minCount = signal<number>(0);
    readonly maxCount = signal<number>(0);
    readonly errorCategories = signal<string[]>([]);

    private isFeedbackDetailChannelModalOpen = false;

    private readonly debounceLoadData = BaseApiHttpService.debounce(this.loadData.bind(this), 300);

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

        try {
            const response = await this.feedbackAnalysisService.search(state, {
                exerciseId: this.exerciseId(),
                filters: {
                    tasks: this.selectedFiltersCount() !== 0 ? savedTasks : [],
                    testCases: this.selectedFiltersCount() !== 0 ? savedTestCases : [],
                    occurrence: this.selectedFiltersCount() !== 0 ? savedOccurrence : [],
                    errorCategories: this.selectedFiltersCount() !== 0 ? savedErrorCategories : [],
                },
            });
            this.content.set(response.feedbackDetails);
            this.totalItems.set(response.totalItems);
            this.taskNames.set(response.taskNames);
            this.testCaseNames.set(response.testCaseNames);
            this.errorCategories.set(response.errorCategories);
        } catch (error) {
            this.alertService.error(this.TRANSLATION_BASE + '.error');
        }
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
        this.maxCount.set(await this.feedbackAnalysisService.getMaxCount(this.exerciseId()));

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
        modalRef.componentInstance.exerciseId = this.exerciseId;
        modalRef.componentInstance.feedbackDetail = signal(feedbackDetail);
    }

    async openFeedbackDetailChannelModal(feedbackDetail: FeedbackDetail): Promise<void> {
        if (this.isFeedbackDetailChannelModalOpen) {
            return;
        }
        this.isFeedbackDetailChannelModalOpen = true;
        const modalRef = this.modalService.open(FeedbackDetailChannelModalComponent, { centered: true, size: 'lg' });
        modalRef.componentInstance.affectedStudentsCount = await this.feedbackAnalysisService.getAffectedStudentCount(this.exerciseId(), feedbackDetail.detailText);
        modalRef.componentInstance.feedbackDetail = signal(feedbackDetail);
        modalRef.componentInstance.formSubmitted.subscribe(async ({ channelDto, navigate }: { channelDto: ChannelDTO; navigate: boolean }) => {
            try {
                const feedbackChannelRequest: FeedbackChannelRequestDTO = {
                    channel: channelDto,
                    feedbackDetailText: feedbackDetail.detailText,
                };
                const createdChannel = await this.feedbackAnalysisService.createChannel(this.courseId(), this.exerciseId(), feedbackChannelRequest);
                const name = createdChannel.name;
                this.alertService.success(this.TRANSLATION_BASE + '.channelSuccess', { name });
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
}
