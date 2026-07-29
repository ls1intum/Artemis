import { ChangeDetectionStrategy, Component, ViewEncapsulation, computed, inject, signal } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ActivatedRoute } from '@angular/router';
import { Course } from 'app/course/shared/entities/course.model';
import { Observable, finalize, forkJoin, map, of, switchMap } from 'rxjs';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { IrisVerdict, IrisVerdictReview } from 'app/iris/shared/entities/iris-verdict.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faFilter, faSync } from '@fortawesome/free-solid-svg-icons';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { BaseEntity } from 'app/foundation/model/base-entity';
import { IrisAssessmentReviewExerciseComponent } from 'app/iris/overview/understanding-assessment/assessment-review-overview/iris-assessment-review-exercise.component';
import { FormsModule } from '@angular/forms';
import { MultiSelectModule } from 'primeng/multiselect';
import { toSignal } from '@angular/core/rxjs-interop';
import { IrisAssessmentReviewService } from 'app/iris/overview/services/iris-assessment-review.service';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';

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
    readonly participations: ProgrammingExerciseStudentParticipation[];
    readonly searchedAndFilteredParticipations: ProgrammingExerciseStudentParticipation[];
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
    imports: [ArtemisTranslatePipe, FaIconComponent, TranslateDirective, IrisAssessmentReviewExerciseComponent, FormsModule, MultiSelectModule, HelpIconComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IrisAssessmentReviewOverviewComponent {
    readonly FilterProp = FilterProp;

    private route = inject(ActivatedRoute);
    private assessmentReviewService = inject(IrisAssessmentReviewService);
    private irisSettingsService = inject(IrisSettingsService);

    protected readonly faSync = faSync;
    protected readonly faFilter = faFilter;

    /**
     * An empty array means that no verdict filter is active, i.e. all participations are shown.
     * Multiple selected verdicts are combined with OR.
     */
    protected readonly selectedFilters = signal<FilterProp[]>([]);
    protected readonly searchTerm = signal('');
    protected readonly exercises = signal<ExerciseViewModel[]>([]);
    protected readonly course = toSignal(this.route.data.pipe(map((data) => data['course'] as Course)), { requireSync: true });
    protected readonly showStartInClassQuizButton = toSignal(this.route.data.pipe(map((data) => !!data['showStartInClassQuizButton'])), {
        requireSync: true,
    });
    protected readonly isLoading = signal(true);

    private readonly normalizedSearchTerm = computed(() => this.searchTerm().trim().toLowerCase());

    private readonly searchedParticipations = computed(() => {
        const normalizedSearchTerm = this.normalizedSearchTerm();

        return this.exercises()
            .flatMap((viewExercise) => viewExercise.participations)
            .filter((participation) => this.filterParticipationByString(participation, normalizedSearchTerm));
    });

    protected readonly searchNoResults = computed(() => this.relevantExercises().length === 0);

    protected readonly relevantExercises = computed<ExerciseViewModel[]>(() => {
        const selectedFilters = this.selectedFilters();
        const normalizedSearchTerm = this.normalizedSearchTerm();

        return this.exercises()
            .map((viewExercise) => ({
                ...viewExercise,
                searchedAndFilteredParticipations: viewExercise.participations.filter(
                    (participation) =>
                        this.filterParticipationBySelectedFilters(participation, selectedFilters) && this.filterParticipationByString(participation, normalizedSearchTerm),
                ),
            }))
            .filter((viewExercise) => viewExercise.searchedAndFilteredParticipations.length > 0);
    });

    protected readonly allSearchedAndFilteredParticipations = computed(() => this.relevantExercises().flatMap((exercise) => exercise.searchedAndFilteredParticipations));

    /** Number of participations matching the text search before applying verdict filters. */
    protected readonly searchedParticipationCount = computed(() => this.searchedParticipations().length);

    /**
     * Counts are based on the current text search, but not on the selected verdict filters.
     * This keeps every option's count meaningful while multiple filters are selected.
     */
    protected readonly participationsPerFilter = computed<ReadonlyMap<FilterProp, number>>(() => {
        const searchedParticipations = this.searchedParticipations();
        const counts = new Map<FilterProp, number>();

        counts.set(FilterProp.ALL, searchedParticipations.length);

        for (const filter of Object.values(FilterProp).filter((value) => value !== FilterProp.ALL)) {
            counts.set(filter, searchedParticipations.filter((participation) => this.filterParticipationByProp(participation, filter)).length);
        }

        return counts;
    });

    protected readonly filterOptions = computed<FilterOption[]>(() =>
        Object.values(FilterProp)
            .filter((value) => value !== FilterProp.ALL)
            .map((value) => ({
                value,
                translationKey: `artemisApp.iris.assessmentReviewOverview.show${value}`,
                count: this.participationsPerFilter().get(value) ?? 0,
            })),
    );

    constructor() {
        const course = this.course();

        const programmingExercises = course.exercises?.filter((exercise: Exercise) => exercise.type === ExerciseType.PROGRAMMING && !exercise.teamMode) ?? [];

        const enabledExercises = this.irisSettingsService.getCourseSettingsWithRateLimit(course.id!).pipe(
            map((settings) => (settings?.settings?.promptingModeEnabled ? programmingExercises : [])),
            switchMap((exercises) => {
                const participationRequests = exercises.map((exercise) =>
                    this.findAllParticipationsNonZeroLatestScoreByProgrammingExercise(exercise.id!).pipe(
                        map((response) => this.createExerciseViewModel(exercise, response.body ?? [])),
                    ),
                );

                return participationRequests.length > 0 ? forkJoin(participationRequests) : of([]);
            }),
            finalize(() => this.isLoading.set(false)),
        );

        (programmingExercises.length > 0 ? enabledExercises : of([])).subscribe((exercises) => this.exercises.set(exercises));
    }

    updateParticipationFilters(filters: FilterProp[] | null | undefined): void {
        this.selectedFilters.set(filters ?? []);
    }

    /**
     * The selecteditems template may expose either the raw enum value or the option object,
     * depending on the installed PrimeNG version/configuration.
     */
    protected getFilterValue(filter: FilterProp | FilterOption): FilterProp {
        return typeof filter === 'string' ? filter : filter.value;
    }

    refresh(): void {
        this.isLoading.set(true);

        const requests = this.exercises().map((viewModel) =>
            this.findAllParticipationsNonZeroLatestScoreByProgrammingExercise(viewModel.exercise.id!).pipe(
                map((response) => this.createExerciseViewModel(viewModel.exercise, response.body ?? [])),
            ),
        );

        (requests.length > 0 ? forkJoin(requests) : of([])).pipe(finalize(() => this.isLoading.set(false))).subscribe((exercises) => this.exercises.set(exercises));
    }

    private createExerciseViewModel(exercise: ProgrammingExercise, participations: ProgrammingExerciseStudentParticipation[]): ExerciseViewModel {
        return {
            exercise,
            id: exercise.id,
            participations,
            searchedAndFilteredParticipations: participations,
        };
    }

    private findAllParticipationsNonZeroLatestScoreByProgrammingExercise(exerciseId: number): Observable<HttpResponse<ProgrammingExerciseStudentParticipation[]>> {
        return this.assessmentReviewService.findAllParticipationsNonZeroLatestScoreByProgrammingExercise(exerciseId, this.showStartInClassQuizButton());
    }

    private filterParticipationBySelectedFilters(participation: ProgrammingExerciseStudentParticipation, filters: readonly FilterProp[]): boolean {
        return filters.length === 0 || filters.some((filter) => this.filterParticipationByProp(participation, filter));
    }

    private filterParticipationByProp(participation: ProgrammingExerciseStudentParticipation, filter: FilterProp): boolean {
        const verdictReview = participation.irisAssessment?.verdictReview;
        const verdict = participation.irisAssessment?.verdict;

        const unreviewed = verdictReview === undefined;

        switch (filter) {
            case FilterProp.UNSUSPICIOUS:
                return verdict === IrisVerdict.UNSUSPICIOUS && unreviewed;
            case FilterProp.SUSPICIOUS:
                return verdict === IrisVerdict.SUSPICIOUS && unreviewed;
            case FilterProp.ACCEPTED:
                return verdictReview === IrisVerdictReview.ACCEPTED;
            case FilterProp.REJECTED:
                return verdictReview === IrisVerdictReview.REJECTED;
            case FilterProp.MISSING: {
                return verdict === undefined;
            }
            case FilterProp.ALL:
            default:
                return true;
        }
    }

    private filterParticipationByString(participation: ProgrammingExerciseStudentParticipation, searchTerm: string): boolean {
        if (searchTerm.length === 0) {
            return true;
        }

        const name = participation.student?.name?.toLowerCase() ?? '';
        const login = participation.student?.login?.toLowerCase() ?? '';

        return name.includes(searchTerm) || login.includes(searchTerm);
    }
}
