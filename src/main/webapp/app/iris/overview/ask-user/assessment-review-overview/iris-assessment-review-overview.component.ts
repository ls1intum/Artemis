import { ChangeDetectionStrategy, Component, DestroyRef, ViewEncapsulation, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ActivatedRoute } from '@angular/router';
import { Course } from 'app/course/shared/entities/course.model';
import { map } from 'rxjs';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faFilter, faSync } from '@fortawesome/free-solid-svg-icons';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { BaseEntity } from 'app/foundation/model/base-entity';
import { IrisAssessmentReviewExerciseComponent } from 'app/iris/overview/ask-user/assessment-review-overview/iris-assessment-review-exercise.component';
import { FormsModule } from '@angular/forms';
import { MultiSelectModule } from 'primeng/multiselect';
import { toSignal } from '@angular/core/rxjs-interop';
import {
    IrisAssessmentReviewHttpService,
    IrisAssessmentReviewParticipation,
    IrisAssessmentReviewSearch,
} from 'app/iris/overview/ask-user/services/iris-assessment-review-http.service';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { SearchFilterComponent } from 'app/shared-ui/search-filter/search-filter.component';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';
import { TableLazyLoadEvent } from 'primeng/table';
import { buildDbQueryFromLazyEvent } from 'app/shared-ui/table-view/request-builder';
import { AlertService } from 'app/foundation/service/alert.service';
import { onError } from 'app/foundation/util/global.utils';

/**
 * Filter properties for a result
 */
export enum FilterProp {
    ALL = 'All',
    ACCEPTED = 'Accepted',
    REJECTED = 'Rejected',
    UNSUSPICIOUS = 'Unsuspicious',
    SUSPICIOUS = 'Suspicious',
    MISSING = 'MissingAssessment', // When IrisVerdictReview is undefined or null
}

export type ExerciseViewModel = BaseEntity & {
    readonly exercise: ProgrammingExercise;
    readonly participations: IrisAssessmentReviewParticipation[];
    readonly searchedAndFilteredParticipations: IrisAssessmentReviewParticipation[];
};

interface FilterOption {
    readonly value: FilterProp;
    readonly translationKey: string;
    readonly count: number;
}

@Component({
    selector: 'jhi-iris-assessment-review-overview',
    templateUrl: './iris-assessment-review-overview.component.html',
    styleUrl: './iris-assessment-review-overview.component.scss',
    encapsulation: ViewEncapsulation.None,
    imports: [
        ArtemisTranslatePipe,
        FaIconComponent,
        TranslateDirective,
        IrisAssessmentReviewExerciseComponent,
        FormsModule,
        MultiSelectModule,
        HelpIconComponent,
        SearchFilterComponent,
        PaginatorModule,
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IrisAssessmentReviewOverviewComponent {
    private static readonly SEARCH_DEBOUNCE_MS = 300;

    readonly FilterProp = FilterProp;

    private readonly route = inject(ActivatedRoute);
    private readonly assessmentReviewService = inject(IrisAssessmentReviewHttpService);
    private readonly irisSettingsService = inject(IrisSettingsService);
    private readonly alertService = inject(AlertService);
    private readonly destroyRef = inject(DestroyRef);

    protected readonly faSync = faSync;
    protected readonly faFilter = faFilter;
    protected readonly pageSizeOptions = [10, 20, 50, 100, 200];

    /**
     * An empty array means that no verdict filter is active, i.e. all participations are shown.
     * Multiple selected verdicts are combined with OR on the server.
     */
    protected readonly selectedFilters = signal<FilterProp[]>([]);
    protected readonly searchTerm = signal('');
    protected readonly exercises = signal<ExerciseViewModel[]>([]);
    protected readonly course = toSignal(this.route.data.pipe(map((data) => data['course'] as Course)), { requireSync: true });
    protected readonly showStartInClassQuizButton = toSignal(this.route.data.pipe(map((data) => !!data['showStartInClassQuizButton'])), {
        requireSync: true,
    });
    protected readonly isLoading = signal(true);
    protected readonly totalRows = signal(0);
    protected readonly first = signal(0);
    protected readonly rows = signal(50);
    protected readonly participationsPerFilter = signal<ReadonlyMap<FilterProp, number>>(new Map([[FilterProp.ALL, 0]]));

    protected readonly searchNoResults = computed(() => !this.isLoading() && this.exercises().length === 0);
    protected readonly itemRangeBegin = computed(() => (this.totalRows() === 0 ? 0 : Math.min(this.totalRows(), this.first() + 1)));
    protected readonly itemRangeEnd = computed(() => Math.min(this.totalRows(), this.first() + this.rows()));

    private readonly programmingExercises = computed<ProgrammingExercise[]>(
        () => this.course().exercises?.filter((exercise: Exercise): exercise is ProgrammingExercise => exercise.type === ExerciseType.PROGRAMMING && !exercise.teamMode) ?? [],
    );

    protected readonly filterOptions = computed<FilterOption[]>(() =>
        Object.values(FilterProp)
            .filter((value) => value !== FilterProp.ALL)
            .map((value) => ({
                value,
                translationKey: `artemisApp.iris.assessmentReviewOverview.show${value}`,
                count: this.participationsPerFilter().get(value) ?? 0,
            })),
    );

    private currentLoadRequestId = 0;
    private searchDebounceTimer: ReturnType<typeof setTimeout> | undefined;

    constructor() {
        this.destroyRef.onDestroy(() => clearTimeout(this.searchDebounceTimer));

        if (this.programmingExercises().length === 0) {
            this.isLoading.set(false);
            return;
        }

        this.irisSettingsService.getCourseSettingsWithRateLimit(this.course().id!).subscribe({
            next: (settings) => {
                if (settings?.settings?.askUserModeEnabled) {
                    this.loadPage();
                } else {
                    this.isLoading.set(false);
                }
            },
            error: (error: HttpErrorResponse) => {
                this.isLoading.set(false);
                onError(this.alertService, error);
            },
        });
    }

    updateParticipationFilters(filters: FilterProp[] | null | undefined): void {
        this.selectedFilters.set(filters ?? []);
        this.first.set(0);
        this.loadPage();
    }

    onSearch(searchTerm: string): void {
        this.searchTerm.set(searchTerm);
        clearTimeout(this.searchDebounceTimer);
        this.searchDebounceTimer = setTimeout(() => {
            this.first.set(0);
            this.loadPage();
        }, IrisAssessmentReviewOverviewComponent.SEARCH_DEBOUNCE_MS);
    }

    onPageChange(event: PaginatorState): void {
        this.first.set(event.first ?? 0);
        this.rows.set(event.rows ?? this.rows());
        this.loadPage();
    }

    /**
     * The selecteditems template may expose either the raw enum value or the option object,
     * depending on the installed PrimeNG version/configuration.
     */
    protected getFilterValue(filter: FilterProp | FilterOption): FilterProp {
        return typeof filter === 'string' ? filter : filter.value;
    }

    refresh(): void {
        this.loadPage();
    }

    private loadPage(): void {
        const courseId = this.course().id;
        if (courseId === undefined) {
            return;
        }

        this.isLoading.set(true);
        const requestId = ++this.currentLoadRequestId;

        this.assessmentReviewService.searchAssessmentReviewParticipations(courseId, this.buildSearch(), this.showStartInClassQuizButton()).subscribe({
            next: (result) => {
                if (requestId === this.currentLoadRequestId) {
                    this.exercises.set(this.createExerciseViewModels(result.content));
                    this.totalRows.set(result.totalElements);
                    this.setParticipationsPerFilter(result.participationsPerFilter);
                }
            },
            error: (error: HttpErrorResponse) => {
                if (requestId === this.currentLoadRequestId) {
                    this.isLoading.set(false);
                    onError(this.alertService, error);
                }
            },
            complete: () => {
                if (requestId === this.currentLoadRequestId) {
                    this.isLoading.set(false);
                }
            },
        });
    }

    private buildSearch(): IrisAssessmentReviewSearch {
        const lazyLoadEvent: TableLazyLoadEvent = {
            first: this.first(),
            rows: this.rows(),
            sortField: 'id',
            sortOrder: 1,
            globalFilter: this.searchTerm(),
        };

        const search = buildDbQueryFromLazyEvent(lazyLoadEvent);
        return {
            page: search.page,
            pageSize: search.pageSize,
            sortingOrder: search.sortingOrder,
            sortedColumn: search.sortedColumn,
            searchTerm: search.searchTerm,
            filterProps: this.selectedFilters(),
        };
    }

    private createExerciseViewModels(participations: IrisAssessmentReviewParticipation[]): ExerciseViewModel[] {
        const exerciseById = new Map<number, ProgrammingExercise>();
        for (const exercise of this.programmingExercises()) {
            if (exercise.id !== undefined) {
                exerciseById.set(exercise.id, exercise);
            }
        }

        const exerciseIdsInPageOrder: number[] = [];
        const participationsByExerciseId = new Map<number, IrisAssessmentReviewParticipation[]>();

        for (const participation of participations) {
            const exerciseId = participation.exerciseId;
            if (exerciseId === undefined || !exerciseById.has(exerciseId)) {
                continue;
            }

            let exerciseParticipations = participationsByExerciseId.get(exerciseId);
            if (!exerciseParticipations) {
                exerciseParticipations = [];
                participationsByExerciseId.set(exerciseId, exerciseParticipations);
                exerciseIdsInPageOrder.push(exerciseId);
            }
            exerciseParticipations.push(participation);
        }

        return exerciseIdsInPageOrder.map((exerciseId) => {
            const exercise = exerciseById.get(exerciseId)!;
            const exerciseParticipations = participationsByExerciseId.get(exerciseId) ?? [];

            return {
                exercise,
                id: exercise.id,
                participations: exerciseParticipations,
                searchedAndFilteredParticipations: exerciseParticipations,
            };
        });
    }

    private setParticipationsPerFilter(participationsPerFilter: ReadonlyMap<string, number>): void {
        const typedCounts = new Map<FilterProp, number>();
        for (const filter of Object.values(FilterProp)) {
            typedCounts.set(filter, participationsPerFilter.get(filter) ?? 0);
        }
        this.participationsPerFilter.set(typedCounts);
    }
}
