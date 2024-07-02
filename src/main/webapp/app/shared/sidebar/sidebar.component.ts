import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output } from '@angular/core';
import { faFilter } from '@fortawesome/free-solid-svg-icons';
import { ActivatedRoute, Params } from '@angular/router';
import { Subscription, distinctUntilChanged } from 'rxjs';
import { ProfileService } from '../layouts/profiles/profile.service';
import { ChannelAccordionShowAdd, ChannelTypeIcons, CollapseState, SidebarData } from 'app/types/sidebar';
import { SidebarEventService } from './sidebar-event.service';
import { ExerciseFilterModalComponent } from 'app/shared/exercise-filter/exercise-filter-modal.component';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { cloneDeep } from 'lodash-es';
import { ExerciseFilterOptions, ExerciseFilterResults } from 'app/types/exercise-filter';
import {
    getAchievablePointsAndAchievedScoreFilterOptions,
    getExerciseCategoryFilterOptions,
    getExerciseDifficultyFilterOptions,
    getExerciseTypeFilterOptions,
} from 'app/shared/sidebar/sidebar.helper';

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

        const scoreAndPointsFilterOptions = getAchievablePointsAndAchievedScoreFilterOptions(this.exerciseFilters, this.sidebarData);

        this.exerciseFilters = {
            categoryFilters: getExerciseCategoryFilterOptions(this.exerciseFilters, this.sidebarData),
            exerciseTypesFilter: getExerciseTypeFilterOptions(this.exerciseFilters, this.sidebarData),
            difficultyFilters: getExerciseDifficultyFilterOptions(this.exerciseFilters, this.sidebarData),
            achievedScore: scoreAndPointsFilterOptions?.achievedScore,
            achievablePoints: scoreAndPointsFilterOptions?.achievablePoints,
        };
    }
}
