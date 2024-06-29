import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output } from '@angular/core';
import { faFilter } from '@fortawesome/free-solid-svg-icons';
import { ActivatedRoute, Params } from '@angular/router';
import { Subscription, distinctUntilChanged } from 'rxjs';
import { ProfileService } from '../layouts/profiles/profile.service';
import { ChannelAccordionShowAdd, ChannelTypeIcons, CollapseState, SidebarCardElement, SidebarData } from 'app/types/sidebar';
import { SidebarEventService } from './sidebar-event.service';
import { ExerciseFilterModalComponent } from 'app/shared/exercise-filter/exercise-filter-modal.component';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { DifficultyLevel } from 'app/entities/exercise.model';
import { cloneDeep } from 'lodash-es';
import { getLatestResultOfStudentParticipation } from 'app/exercises/shared/participation/participation.utils';
import { DifficultyFilterOptions, ExerciseFilterOptions, ExerciseFilterResults, RangeFilter } from 'app/types/exercise-filter';
import { getExerciseCategoryFilterOptions, getExerciseTypeFilterOptions } from 'app/shared/sidebar/sidebar.helper';

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

@Component({
    selector: 'jhi-sidebar',
    templateUrl: './sidebar.component.html',
    styleUrls: ['./sidebar.component.scss'],
})
export class SidebarComponent implements OnDestroy, OnChanges, OnInit {
    @Output() onSelectConversation = new EventEmitter<number>();
    @Output() onUpdateSidebar = new EventEmitter<void>();
    @Output() onPlusPressed = new EventEmitter<string>();
    @Input() searchFieldEnabled: boolean = true;
    @Input() sidebarData: SidebarData;
    @Input() courseId?: number;
    @Input() itemSelected?: boolean;
    @Input() showAddOption?: ChannelAccordionShowAdd;
    @Input() channelTypeIcon?: ChannelTypeIcons;
    @Input() collapseState: CollapseState;

    searchValue = '';
    isCollapsed: boolean = false;

    exerciseId: string;

    paramSubscription?: Subscription;
    profileSubscription?: Subscription;
    sidebarEventSubscription?: Subscription;
    sidebarAccordionEventSubscription?: Subscription;
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
                    if (this.sidebarData.sidebarType == 'conversation') {
                        this.onSelectConversation.emit(+itemId);
                        this.onUpdateSidebar.emit();
                    }
                }
            });

        this.sidebarAccordionEventSubscription = this.sidebarEventService
            .sidebarAccordionPlusClickedEventListener()
            .pipe(
                distinctUntilChanged(), // This ensures the function is only called when the actual value changes
            )
            .subscribe((groupKey) => {
                if (groupKey) {
                    this.onPlusPressed.emit(groupKey);
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

    getSize() {
        const size = {
            ['exercise']: 'M',
            ['default']: 'M',
            ['conversation']: 'S',
            ['exam']: 'L',
        };
        return this.sidebarData.sidebarType ? size[this.sidebarData.sidebarType] : 'M';
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
            categoryFilters: getExerciseCategoryFilterOptions(this.exerciseFilters, this.sidebarData),
            exerciseTypesFilter: getExerciseTypeFilterOptions(this.exerciseFilters, this.sidebarData),
            difficultyFilters: this.getExerciseDifficultyFilterOptions(),
            achievedScore: scoreAndPointsFilterOptions?.achievedScore,
            achievablePoints: scoreAndPointsFilterOptions?.achievablePoints,
        };
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
