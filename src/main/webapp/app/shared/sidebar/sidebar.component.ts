import { Component, Input, OnChanges, OnDestroy, OnInit } from '@angular/core';
import { IconDefinition, faCheckDouble, faDiagramProject, faFileArrowUp, faFilter, faFont, faKeyboard } from '@fortawesome/free-solid-svg-icons';
import { ActivatedRoute, Params } from '@angular/router';
import { Subscription, distinctUntilChanged } from 'rxjs';
import { ProfileService } from '../layouts/profiles/profile.service';
import { SidebarCardElement, SidebarData } from 'app/types/sidebar';
import { SidebarEventService } from './sidebar-event.service';
import { ExerciseFilterModalComponent } from 'app/shared/exercise-filter/exercise-filter-modal.component';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { DifficultyLevel, ExerciseType } from 'app/entities/exercise.model';
import { cloneDeep } from 'lodash-es';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { getLatestResultOfStudentParticipation } from 'app/exercises/shared/participation/participation.utils';

export type ExerciseCategoryFilterOption = { category: ExerciseCategory; searched: boolean };
export type ExerciseTypeFilterOptions = { name: string; value: ExerciseType; checked: boolean; icon: IconDefinition }[];
export type DifficultyFilterOptions = { name: string; value: DifficultyLevel; checked: boolean }[];
export type RangeFilter = { generalMin: number; generalMax: number; selectedMin: number; selectedMax: number; step: number };

export type ExerciseFilterOptions = {
    categoryFilters?: ExerciseCategoryFilterOption[];
    exerciseTypesFilter?: ExerciseTypeFilterOptions;
    // dueDateRange: RangeFilter;
    difficultyFilters?: DifficultyFilterOptions;
    achievedScore?: RangeFilter;
    achievablePoints?: RangeFilter;
};

export type ExerciseFilterResults = { filteredSidebarData?: SidebarData; appliedExerciseFilters?: ExerciseFilterOptions };

const POINTS_STEP = 1;
const SCORE_THRESHOLD_TO_INCREASE_STEP = 20;
const SMALL_SCORE_STEP = 1;
const SCORE_STEP = 5;

// TODO allow to filter for no difficulty?
const DEFAULT_DIFFICULTIES_FILTER: DifficultyFilterOptions = [
    { name: 'artemisApp.exercise.easy', value: DifficultyLevel.EASY, checked: false },
    { name: 'artemisApp.exercise.medium', value: DifficultyLevel.MEDIUM, checked: false },
    { name: 'artemisApp.exercise.hard', value: DifficultyLevel.HARD, checked: false },
];

const DEFAULT_EXERCISE_TYPES_FILTER: ExerciseTypeFilterOptions = [
    { name: 'artemisApp.courseStatistics.programming', value: ExerciseType.PROGRAMMING, checked: false, icon: faKeyboard },
    { name: 'artemisApp.courseStatistics.quiz', value: ExerciseType.QUIZ, checked: false, icon: faCheckDouble },
    { name: 'artemisApp.courseStatistics.modeling', value: ExerciseType.MODELING, checked: false, icon: faDiagramProject },
    { name: 'artemisApp.courseStatistics.text', value: ExerciseType.TEXT, checked: false, icon: faFont },
    { name: 'artemisApp.courseStatistics.file-upload', value: ExerciseType.FILE_UPLOAD, checked: false, icon: faFileArrowUp },
];

@Component({
    selector: 'jhi-sidebar',
    templateUrl: './sidebar.component.html',
    styleUrls: ['./sidebar.component.scss'],
})
export class SidebarComponent implements OnDestroy, OnChanges, OnInit {
    @Input() searchFieldEnabled: boolean = true;
    @Input() sidebarData: SidebarData;
    @Input() courseId?: number;
    @Input() itemSelected?: boolean;

    searchValue = '';
    isCollapsed: boolean = false;

    exerciseId: string;

    paramSubscription?: Subscription;
    profileSubscription?: Subscription;
    sidebarEventSubscription?: Subscription;
    routeParams: Params;
    isProduction = true;
    isTestServer = false;

    private modalRef: NgbModalRef | null;

    readonly faFilter = faFilter;

    sidebarDataBeforeFiltering: SidebarData;

    exerciseFilters?: ExerciseFilterOptions;

    constructor(
        private route: ActivatedRoute,
        private profileService: ProfileService,
        private sidebarEventService: SidebarEventService,
        private modalService: NgbModal,
    ) {}

    ngOnInit(): void {
        this.profileSubscription = this.profileService.getProfileInfo()?.subscribe((profileInfo) => {
            this.isProduction = profileInfo?.inProduction;
            this.isTestServer = profileInfo?.testServer ?? false;
        });
        this.sidebarEventSubscription = this.sidebarEventService
            .sidebarCardEventListener()
            .pipe(
                distinctUntilChanged(), // This ensures the function is only called when the actual value changes
            )
            .subscribe((itemId) => {
                if (itemId) {
                    this.storeLastSelectedItem(itemId);
                }
            });
    }

    ngOnChanges() {
        this.paramSubscription = this.route.params?.subscribe((params) => {
            this.routeParams = params;
        });
    }

    setSearchValue(searchValue: string) {
        this.searchValue = searchValue;
    }

    storeLastSelectedItem(itemId: number | string) {
        sessionStorage.setItem('sidebar.lastSelectedItem.' + this.sidebarData.storageId + '.byCourse.' + this.courseId, JSON.stringify(itemId));
    }

    ngOnDestroy() {
        this.paramSubscription?.unsubscribe();
        this.profileSubscription?.unsubscribe();
        this.sidebarEventSubscription?.unsubscribe();
        this.sidebarEventService.emitResetValue();
    }

    openFilterExercisesDialog() {
        // TODO uncollapse all groups when a filter is active
        // TODO display a message if filter options lead to no results

        this.initializeFilterOptions();

        if (!this.sidebarDataBeforeFiltering) {
            this.sidebarDataBeforeFiltering = cloneDeep(this.sidebarData);
        }

        this.modalRef = this.modalService.open(ExerciseFilterModalComponent, {
            size: 'lg',
            backdrop: 'static',
            animation: true,
        });

        this.modalRef.componentInstance.sidebarData = cloneDeep(this.sidebarDataBeforeFiltering);
        this.modalRef.componentInstance.exerciseFilters = cloneDeep(this.exerciseFilters);

        this.modalRef.componentInstance.filterApplied.subscribe((exerciseFilterResults: ExerciseFilterResults) => {
            this.sidebarData = exerciseFilterResults.filteredSidebarData!;
            this.exerciseFilters = exerciseFilterResults.appliedExerciseFilters;

            console.log('Filter applied', exerciseFilterResults);
        });
    }

    // TODO handle course switching (reset filters when switching courses)

    // TODO dont display the filter option if no filter option is reasonable

    private initializeFilterOptions() {
        if (this.exerciseFilters) {
            return;
        }

        const scoreAndPointsFilterOptions = this.getAchievablePointsAndAchievedScoreFilterOptions();

        this.exerciseFilters = {
            categoryFilters: this.getExerciseCategoryFilterOptions(),
            exerciseTypesFilter: this.getExerciseTypeFilterOptions(),
            difficultyFilters: this.getExerciseDifficultyFilterOptions(),
            achievedScore: scoreAndPointsFilterOptions?.achievedScore,
            achievablePoints: scoreAndPointsFilterOptions?.achievablePoints,
        };
    }

    private getExerciseCategoryFilterOptions() {
        if (this.exerciseFilters?.categoryFilters) {
            return this.exerciseFilters?.categoryFilters;
        }

        const categoryFilters =
            this.sidebarData?.ungroupedData
                ?.filter((sidebarElement: SidebarCardElement) => sidebarElement.exercise?.categories !== undefined)
                .flatMap((sidebarElement: SidebarCardElement) => sidebarElement.exercise?.categories || [])
                .map((category: ExerciseCategory) => ({ category: category, searched: false })) ?? [];

        this.sortCategoriesAlphanumerically(categoryFilters);

        return categoryFilters;
    }

    private sortCategoriesAlphanumerically(categoryFilters: ExerciseCategoryFilterOption[]) {
        categoryFilters.sort((categoryA, categoryB) => {
            const categoryADisplayText = categoryA.category.category?.toLowerCase() ?? '';
            const categoryBDisplayText = categoryB.category.category?.toLowerCase() ?? '';
            if (categoryADisplayText < categoryBDisplayText) {
                return -1;
            }
            if (categoryADisplayText > categoryBDisplayText) {
                return 1;
            }
            return 0;
        });
    }

    private getExerciseTypeFilterOptions() {
        if (this.exerciseFilters?.exerciseTypesFilter) {
            return this.exerciseFilters?.exerciseTypesFilter;
        }

        const existingExerciseTypes = this.sidebarData?.ungroupedData
            ?.filter((sidebarElement: SidebarCardElement) => sidebarElement.type !== undefined)
            .map((sidebarElement: SidebarCardElement) => sidebarElement.type);

        return DEFAULT_EXERCISE_TYPES_FILTER?.filter((exerciseType) => existingExerciseTypes?.includes(exerciseType.value));
    }

    private getExerciseDifficultyFilterOptions() {
        if (this.exerciseFilters?.difficultyFilters) {
            return this.exerciseFilters.difficultyFilters;
        }
        // TODO handle noLevel difficulty

        const existingDifficulties = this.sidebarData?.ungroupedData
            ?.filter((sidebarElement: SidebarCardElement) => sidebarElement.difficulty !== undefined)
            .map((sidebarElement: SidebarCardElement) => sidebarElement.difficulty);

        return DEFAULT_DIFFICULTIES_FILTER?.filter((difficulty) => existingDifficulties?.includes(difficulty.value));
    }

    private getAchievablePointsAndAchievedScoreFilterOptions():
        | {
              achievablePoints: RangeFilter;
              achievedScore: RangeFilter;
          }
        | undefined {
        // TODO re caluclate the scores, they might have changed since the last filter application

        if ((this.exerciseFilters?.achievablePoints && this.exerciseFilters.achievedScore) || !this.sidebarData?.ungroupedData) {
            return;
        }

        let minAchievablePoints = Infinity;
        let maxAchievablePoints = -Infinity;

        let minAchievedScore = Infinity;
        let maxAchievedScore = -Infinity;

        this.sidebarData.ungroupedData.forEach((sidebarElement: SidebarCardElement) => {
            if (sidebarElement.exercise?.maxPoints) {
                const currentExerciseMaxPoints = sidebarElement.exercise.maxPoints;

                if (currentExerciseMaxPoints > maxAchievablePoints) {
                    maxAchievablePoints = currentExerciseMaxPoints;
                }
                if (currentExerciseMaxPoints < minAchievablePoints) {
                    minAchievablePoints = currentExerciseMaxPoints;
                }

                if (sidebarElement.studentParticipation) {
                    const currentExerciseAchievedScore = getLatestResultOfStudentParticipation(sidebarElement.studentParticipation, true)?.score;

                    if (currentExerciseAchievedScore !== undefined) {
                        if (currentExerciseAchievedScore > maxAchievedScore) {
                            maxAchievedScore = currentExerciseAchievedScore;
                        }
                        if (currentExerciseAchievedScore < minAchievedScore) {
                            minAchievedScore = currentExerciseAchievedScore;
                        }
                    }
                }
            }
        });

        minAchievablePoints = this.roundToMultiple(minAchievablePoints, POINTS_STEP);
        maxAchievablePoints = this.roundToMultiple(maxAchievablePoints, POINTS_STEP);

        minAchievedScore = this.roundToMultiple(minAchievedScore, SMALL_SCORE_STEP);
        maxAchievedScore = this.roundToMultiple(maxAchievedScore, SMALL_SCORE_STEP);

        if (maxAchievedScore > SCORE_THRESHOLD_TO_INCREASE_STEP) {
            minAchievedScore = this.roundToMultiple(minAchievedScore, SCORE_STEP);
            maxAchievedScore = this.roundToMultiple(maxAchievedScore, SCORE_STEP);
        }

        return {
            achievablePoints: {
                generalMin: minAchievablePoints,
                generalMax: maxAchievablePoints,
                selectedMin: minAchievablePoints,
                selectedMax: maxAchievablePoints,
                step: POINTS_STEP,
            },
            achievedScore: {
                generalMin: minAchievedScore,
                generalMax: maxAchievedScore,
                selectedMin: minAchievedScore,
                selectedMax: maxAchievedScore,
                step: maxAchievedScore <= SCORE_THRESHOLD_TO_INCREASE_STEP ? SMALL_SCORE_STEP : SCORE_STEP,
            },
        };
    }

    private roundToMultiple(value: number, multiple: number) {
        return Math.round(value / multiple) * multiple;
    }
}
