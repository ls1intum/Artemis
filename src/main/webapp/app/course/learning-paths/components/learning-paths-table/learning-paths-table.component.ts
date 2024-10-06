import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { LearningPathApiService } from 'app/course/learning-paths/services/learning-path-api.service';
import { AlertService } from 'app/core/util/alert.service';
import { LearningPathInformationDTO } from 'app/entities/competency/learning-path.model';
import { SearchResult, SearchTermPageableSearch, SortingOrder } from 'app/shared/table/pageable-table';
import { onError } from 'app/shared/util/global.utils';
import { faSpinner } from '@fortawesome/free-solid-svg-icons';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { CompetencyGraphModalComponent } from 'app/course/learning-paths/components/competency-graph-modal/competency-graph-modal.component';
import { BaseApiHttpService } from 'app/course/learning-paths/services/base-api-http.service';

enum TableColumn {
    ID = 'ID',
    USER_NAME = 'USER_NAME',
    USER_LOGIN = 'USER_LOGIN',
    PROGRESS = 'PROGRESS',
}

@Component({
    selector: 'jhi-learning-paths-table',
    standalone: true,
    imports: [ArtemisSharedCommonModule],
    templateUrl: './learning-paths-table.component.html',
    styleUrls: ['./learning-paths-table.component.scss', '../../pages/learning-path-instructor-page/learning-path-instructor-page.component.scss'],
})
export class LearningPathsTableComponent {
    protected readonly faSpinner = faSpinner;

    private readonly learningPathApiService = inject(LearningPathApiService);
    private readonly alertService = inject(AlertService);
    private readonly modalService = inject(NgbModal);

    readonly courseId = input.required<number>();

    readonly isLoading = signal<boolean>(false);
    private readonly searchResults = signal<SearchResult<LearningPathInformationDTO> | undefined>(undefined);
    readonly learningPaths = computed(() => this.searchResults()?.resultsOnPage ?? []);

    readonly searchTerm = signal<string>('');
    readonly page = signal<number>(1);
    private readonly sortingOrder = signal<SortingOrder>(SortingOrder.ASCENDING);
    private readonly sortedColumn = signal<TableColumn>(TableColumn.ID);
    readonly pageSize = signal<number>(50).asReadonly();
    readonly collectionSize = computed(() => {
        if (this.learningPaths().length <= this.pageSize()) {
            return this.learningPaths().length;
        } else {
            return (this.searchResults()?.numberOfPages ?? 1) * this.pageSize();
        }
    });

    // Debounce the loadLearningPaths function to prevent multiple requests when the user types quickly
    private readonly debounceLoadLearningPaths = BaseApiHttpService.debounce(this.loadLearningPaths.bind(this), 300);

    constructor() {
        effect(
            () => {
                // Load learning paths whenever the courseId changes
                const courseId = this.courseId();
                untracked(() => this.loadLearningPaths(courseId));
            },
            { allowSignalWrites: true },
        );
    }

    private async loadLearningPaths(courseId: number): Promise<void> {
        try {
            this.isLoading.set(true);
            const searchState = <SearchTermPageableSearch>{
                page: this.page(),
                pageSize: this.pageSize(),
                searchTerm: this.searchTerm(),
                sortingOrder: this.sortingOrder(),
                sortedColumn: this.sortedColumn(),
            };
            const searchResults = await this.learningPathApiService.getLearningPathInformation(courseId, searchState);
            this.searchResults.set(searchResults);
        } catch (error) {
            onError(this.alertService, error);
        } finally {
            this.isLoading.set(false);
        }
    }

    search(searchTerm: string): void {
        this.searchTerm.set(searchTerm);
        this.page.set(1);
        this.debounceLoadLearningPaths(this.courseId());
    }

    async setPage(pageNumber: number): Promise<void> {
        this.page.set(pageNumber);
        await this.loadLearningPaths(this.courseId());
    }

    openCompetencyGraph(learningPathId: number): void {
        CompetencyGraphModalComponent.openCompetencyGraphModal(this.modalService, learningPathId);
    }
}
