import { Component, Input, OnChanges, OnDestroy, OnInit } from '@angular/core';
import { faFilter } from '@fortawesome/free-solid-svg-icons';
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

export type ExerciseTypeFilterOptions = { name: string; value: ExerciseType; checked: boolean }[];
export type DifficultyFilterOptions = { name: string; value: DifficultyLevel; checked: boolean }[];
export type RangeFilter = { min: number; max: number };

// TODO allow to filter for no difficulty?
const DEFAULT_DIFFICULTIES_FILTER: DifficultyFilterOptions = [
    { name: 'artemisApp.exercise.easy', value: DifficultyLevel.EASY, checked: false },
    { name: 'artemisApp.exercise.medium', value: DifficultyLevel.MEDIUM, checked: false },
    { name: 'artemisApp.exercise.hard', value: DifficultyLevel.HARD, checked: false },
];

const DEFAULT_EXERCISE_TYPES_FILTER: ExerciseTypeFilterOptions = [
    { name: 'artemisApp.courseStatistics.programming', value: ExerciseType.PROGRAMMING, checked: false },
    { name: 'artemisApp.courseStatistics.quiz', value: ExerciseType.QUIZ, checked: false },
    { name: 'artemisApp.courseStatistics.modeling', value: ExerciseType.MODELING, checked: false },
    { name: 'artemisApp.courseStatistics.text', value: ExerciseType.TEXT, checked: false },
    { name: 'artemisApp.courseStatistics.file-upload', value: ExerciseType.FILE_UPLOAD, checked: false },
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
    difficultyFilters?: DifficultyFilterOptions;
    possibleCategories?: ExerciseCategory[];
    exerciseTypesFilter = DEFAULT_EXERCISE_TYPES_FILTER;
    pointsFilter?: RangeFilter;
    scoreFilter?: RangeFilter;

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
        this.modalRef.componentInstance.difficultyFilters = this.difficultyFilters;
        this.modalRef.componentInstance.possibleCategories = this.possibleCategories;
        this.modalRef.componentInstance.typeFilters = this.exerciseTypesFilter;
        this.modalRef.componentInstance.filterApplied.subscribe((filteredSidebarData: SidebarData) => {
            this.sidebarData = filteredSidebarData;
        });
    }

    private initializeFilterOptions() {
        // TODO do not display difficulties selection if not enough selection options
        this.initializeDifficultyFilter();
        this.initializeCategoryFilter();
        this.initializeAchievedScoreFilter();
    }

    private initializeDifficultyFilter() {
        if (this.difficultyFilters) {
            return;
        }

        const existingDifficulties = this.sidebarData?.ungroupedData
            ?.filter((sidebarElement: SidebarCardElement) => sidebarElement.difficulty !== undefined)
            .map((sidebarElement: SidebarCardElement) => sidebarElement.difficulty);

        this.difficultyFilters = DEFAULT_DIFFICULTIES_FILTER?.filter((difficulty) => existingDifficulties?.includes(difficulty.value));
    }

    private initializeCategoryFilter() {
        if (this.possibleCategories) {
            return;
        }

        this.possibleCategories =
            this.sidebarData?.ungroupedData
                ?.filter((sidebarElement: SidebarCardElement) => sidebarElement.exercise?.categories !== undefined)
                .flatMap((sidebarElement: SidebarCardElement) => sidebarElement.exercise?.categories || []) ?? [];
    }

    private initializeAchievedScoreFilter() {
        if (this.scoreFilter || !this.sidebarData?.ungroupedData) {
            return;
        }

        let minPoints = Infinity;
        let maxPoints = -Infinity;

        this.sidebarData.ungroupedData.forEach((sidebarElement: SidebarCardElement) => {
            if (sidebarElement.exercise?.maxPoints) {
                const currentExerciseMaxPoints = sidebarElement.exercise.maxPoints;

                if (currentExerciseMaxPoints > maxPoints) {
                    maxPoints = currentExerciseMaxPoints;
                }
                if (currentExerciseMaxPoints < minPoints) {
                    minPoints = currentExerciseMaxPoints;
                }
            }
        });

        this.pointsFilter = { min: minPoints, max: maxPoints };
    }
}
