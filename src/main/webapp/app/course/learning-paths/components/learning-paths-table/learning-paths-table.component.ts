import { Component, computed, effect, inject, input, model, signal } from '@angular/core';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { LearningPathApiService } from 'app/course/learning-paths/services/learning-path-api.service';
import { AlertService } from 'app/core/util/alert.service';
import { LearningPathInformationDTO } from 'app/entities/competency/learning-path.model';
import { SearchResult, SearchTermPageableSearch, SortingOrder } from 'app/shared/table/pageable-table';
import { onError } from 'app/shared/util/global.utils';

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
    styleUrl: './learning-paths-table.component.scss',
})
export class LearningPathsTableComponent {
    private readonly learningPathApiService = inject(LearningPathApiService);
    private readonly alertService = inject(AlertService);

    readonly courseId = input.required<number>();

    readonly isLoading = signal<boolean>(false);
    private readonly searchResults = signal<SearchResult<LearningPathInformationDTO> | undefined>(undefined);
    readonly learningPaths = computed(() => this.searchResults()?.resultsOnPage ?? []);

    readonly searchTerm = model<string>('');
    private readonly page = signal<number>(1);
    private readonly sortingOrder = signal<SortingOrder>(SortingOrder.ASCENDING);
    private readonly sortedColumn = signal<TableColumn>(TableColumn.ID);

    private readonly searchState = computed(() => {
        return <SearchTermPageableSearch>{
            page: this.page(),
            pageSize: 50,
            searchTerm: this.searchTerm(),
            sortingOrder: this.sortingOrder(),
            sortedColumn: this.sortedColumn(),
        };
    });

    constructor() {
        // Load learning paths whenever the search state or courseId changes
        effect(() => this.loadLearningPaths(this.courseId(), this.searchState()), { allowSignalWrites: true });
    }

    private async loadLearningPaths(courseId: number, searchState: SearchTermPageableSearch): Promise<void> {
        try {
            this.isLoading.set(true);
            const searchResults = await this.learningPathApiService.getLearningPathInformation(courseId, searchState);
            this.searchResults.set(searchResults);
        } catch (error) {
            onError(this.alertService, error);
        } finally {
            this.isLoading.set(false);
        }
    }
}
