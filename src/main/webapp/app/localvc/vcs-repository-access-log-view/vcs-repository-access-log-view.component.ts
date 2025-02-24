import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { Observable, lastValueFrom } from 'rxjs';
import { NgbModule, NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { ActivatedRoute } from '@angular/router';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { VcsAccessLogDTO } from 'app/entities/vcs-access-log-entry.model';
import { AlertService } from 'app/core/util/alert.service';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { SearchResult, SortingOrder } from 'app/shared/table/pageable-table';
import { SortIconComponent } from 'app/shared/sort/sort-icon.component';
import { FilterData } from 'app/exercises/programming/manage/grading/feedback-analysis/modal/feedback-filter-modal.component';
import { CommonModule } from '@angular/common';
import { VcsRepositoryAccessLogService } from 'app/localvc/vcs-repository-access-log-view/vcs-repository-access-log.service';

@Component({
    selector: 'jhi-vcs-repository-access-log-view',
    templateUrl: './vcs-repository-access-log-view.component.html',
    imports: [TranslateDirective, NgbModule, NgbPagination, SortIconComponent, CommonModule],
})
export class VcsRepositoryAccessLogViewComponent {
    private readonly route = inject(ActivatedRoute);
    private readonly programmingExerciseParticipationService = inject(ProgrammingExerciseParticipationService);
    private readonly alertService = inject(AlertService);
    private readonly vcsAccessLogService = inject(VcsRepositoryAccessLogService);
    protected readonly vcsAccessLogEntries = signal<VcsAccessLogDTO[]>([]);

    private readonly params = toSignal(this.route.params, { requireSync: true });

    readonly page = signal<number>(1);
    readonly pageSize = signal<number>(2);
    readonly searchTerm = signal<string>('');
    readonly sortingOrder = signal<SortingOrder>(SortingOrder.ASCENDING);
    readonly sortedColumn = signal<string>('id');
    readonly isLoading = signal<boolean>(false);

    readonly content = signal<SearchResult<VcsAccessLogDTO>>({ resultsOnPage: [], numberOfPages: 0 });
    readonly totalItems = signal<number>(0);
    readonly collectionsSize = computed(() => this.content().numberOfPages * this.pageSize());
    readonly selectedFiltersCount = signal<number>(0);

    readonly TRANSLATION_BASE = 'artemisApp.repository.vcsAccessLog';

    private readonly participationId = computed(() => {
        const participationId = this.params().repositoryId;
        if (participationId) {
            return Number(participationId);
        }
        return undefined;
    });
    // private readonly exerciseId = computed(() => Number(this.params().exerciseId));
    // private readonly repositoryType = computed(() => String(this.params().repositoryType));

    constructor() {
        // effect(async () => {
        //     if (this.participationId()) {
        //         await this.loadVcsAccessLogForParticipation(this.participationId()!);
        //     } else {
        //         await this.loadVcsAccessLog(this.exerciseId(), this.repositoryType());
        //     }
        //     await this.loadData();
        // });
        effect(() => {
            untracked(async () => {
                await this.loadData();
            });
        });
    }

    async loadData() {
        const state = {
            page: this.page(),
            pageSize: this.pageSize(),
            searchTerm: this.searchTerm() || '',
            sortingOrder: this.sortingOrder(),
            sortedColumn: this.sortedColumn(),
        };
        this.isLoading.set(true);
        try {
            const response = await this.vcsAccessLogService.search(state, {
                participationId: this.participationId() ?? 0,
                filters: {} as FilterData,
            });
            this.content.set(response);
        } catch (error) {
            this.alertService.error(this.TRANSLATION_BASE + '.error');
        } finally {
            this.isLoading.set(false);
        }
    }

    async setPage(newPage: number) {
        this.page.set(newPage);
        await this.loadData();
    }

    async setSortedColumn(column: string) {
        if (this.sortedColumn() === column) {
            this.sortingOrder.set(this.sortingOrder() === SortingOrder.ASCENDING ? SortingOrder.DESCENDING : SortingOrder.ASCENDING);
        } else {
            this.sortedColumn.set(column);
            this.sortingOrder.set(SortingOrder.ASCENDING);
        }
        await this.loadData();
    }

    getSortDirection(column: string): SortingOrder.ASCENDING | SortingOrder.DESCENDING | 'none' {
        if (this.sortedColumn() === column) {
            return this.sortingOrder() === SortingOrder.ASCENDING ? SortingOrder.ASCENDING : SortingOrder.DESCENDING;
        }
        return 'none';
    }

    applyFilters(filters: FilterData): void {
        // this.selectedFiltersCount.set(this.countAppliedFilters(filters));
        // this.loadData();
    }

    async openFilterModal(): Promise<void> {
        // const savedTasks = this.localStorage.retrieve(this.FILTER_TASKS_KEY);
        // const savedTestCases = this.localStorage.retrieve(this.FILTER_TEST_CASES_KEY);
        // const savedOccurrence = this.localStorage.retrieve(this.FILTER_OCCURRENCE_KEY);
        // const savedErrorCategories = this.localStorage.retrieve(this.FILTER_ERROR_CATEGORIES_KEY);
        // this.minCount.set(0);
        // if (this.groupFeedback()) {
        //     this.maxCount.set(this.maxCount());
        // } else {
        //     this.maxCount.set(await this.feedbackAnalysisService.getMaxCount(this.exerciseId()));
        // }
        //
        // const modalRef = this.modalService.open(FeedbackFilterModalComponent, { centered: true, size: 'lg' });
        //
        // modalRef.componentInstance.exerciseId = this.exerciseId;
        // modalRef.componentInstance.taskArray = this.taskNames;
        // modalRef.componentInstance.testCaseNames = this.testCaseNames;
        // modalRef.componentInstance.maxCount = this.maxCount;
        // modalRef.componentInstance.errorCategories = this.errorCategories;
        // modalRef.componentInstance.filters = {
        //     tasks: this.selectedFiltersCount() !== 0 ? savedTasks : [],
        //     testCases: this.selectedFiltersCount() !== 0 ? savedTestCases : [],
        //     occurrence: this.selectedFiltersCount() !== 0 ? savedOccurrence : [this.minCount(), this.maxCount()],
        //     errorCategories: this.selectedFiltersCount() !== 0 ? savedErrorCategories : [],
        // };
        // modalRef.componentInstance.filterApplied.subscribe((filters: any) => {
        //     this.applyFilters(filters);
        // });
    }

    public async loadVcsAccessLogForParticipation(participationId: number) {
        await this.extractEntries(() => this.programmingExerciseParticipationService.getVcsAccessLogForParticipation(participationId));
    }

    public async loadVcsAccessLog(exerciseId: number, repositoryType: string) {
        await this.extractEntries(() => this.programmingExerciseParticipationService.getVcsAccessLogForRepository(exerciseId, repositoryType));
    }

    private async extractEntries(fetchVcsAccessLogs: () => Observable<VcsAccessLogDTO[] | undefined>) {
        try {
            const accessLogEntries = await lastValueFrom(fetchVcsAccessLogs());
            if (accessLogEntries) {
                this.vcsAccessLogEntries.set(accessLogEntries);
            }
        } catch (error) {
            this.alertService.error('artemisApp.repository.vcsAccessLog.error');
        }
    }
}
