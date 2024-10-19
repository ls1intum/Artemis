import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, input, output } from '@angular/core';
import { faFilter, faFilterCircleXmark, faHashtag, faPlusCircle, faSearch, faUser, faUsers } from '@fortawesome/free-solid-svg-icons';
import { ActivatedRoute, Params } from '@angular/router';
import { Subscription, distinctUntilChanged } from 'rxjs';
import { ProfileService } from '../layouts/profiles/profile.service';
import { ChannelAccordionShowAdd, ChannelTypeIcons, CollapseState, SidebarCardSize, SidebarData, SidebarItemShowAlways, SidebarTypes } from 'app/types/sidebar';
import { SidebarEventService } from './sidebar-event.service';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { cloneDeep } from 'lodash-es';
import { ExerciseFilterOptions, ExerciseFilterResults } from 'app/types/exercise-filter';
import {
    getAchievablePointsAndAchievedScoreFilterOptions,
    getExerciseCategoryFilterOptions,
    getExerciseDifficultyFilterOptions,
    getExerciseTypeFilterOptions,
} from 'app/shared/sidebar/sidebar.helper';
import { ExerciseFilterModalComponent } from 'app/shared/exercise-filter/exercise-filter-modal.component';

@Component({
    selector: 'jhi-sidebar',
    templateUrl: './sidebar.component.html',
    styleUrls: ['./sidebar.component.scss'],
})
export class SidebarComponent implements OnDestroy, OnChanges, OnInit {
    @Output() onSelectConversation = new EventEmitter<number>();
    @Output() onUpdateSidebar = new EventEmitter<void>();
    onDirectChatPressed = output<void>();
    onGroupChatPressed = output<void>();
    onBrowsePressed = output<void>();
    onCreateChannelPressed = output<void>();
    @Input() searchFieldEnabled: boolean = true;
    @Input() sidebarData: SidebarData;
    @Input() courseId?: number;
    @Input() itemSelected?: boolean;
    @Input() showAddOption?: ChannelAccordionShowAdd;
    @Input() channelTypeIcon?: ChannelTypeIcons;
    @Input() collapseState: CollapseState;
    sidebarItemAlwaysShow = input.required<SidebarItemShowAlways>();
    @Input() showFilter: boolean = false;
    inCommunication = input<boolean>(false);
    searchValue = '';
    isCollapsed: boolean = false;

    exerciseId: string;

    paramSubscription?: Subscription;
    profileSubscription?: Subscription;
    sidebarEventSubscription?: Subscription;

    routeParams: Params;
    isProduction = true;
    isTestServer = false;

    private modalRef?: NgbModalRef;

    readonly faFilter = faFilter;
    readonly faFilterCurrentlyApplied = faFilterCircleXmark;
    readonly faUser = faUser;
    readonly faUsers = faUsers;
    readonly faPlusCircle = faPlusCircle;
    readonly faSearch = faSearch;
    readonly faHashtag = faHashtag;

    sidebarDataBeforeFiltering: SidebarData;

    exerciseFilters?: ExerciseFilterOptions;
    isFilterActive: boolean = false;

    constructor(
        private route: ActivatedRoute,
        private profileService: ProfileService,
        private sidebarEventService: SidebarEventService,
        private modalService: NgbModal,
    ) {}

    createNewChannel() {
        this.onCreateChannelPressed.emit();
    }

    browseChannels() {
        this.onBrowsePressed.emit();
    }

    createDirectChat() {
        this.onDirectChatPressed.emit();
    }

    createGroupChat() {
        this.onGroupChatPressed.emit();
    }

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
        const size: Record<SidebarTypes, SidebarCardSize> = {
            ['exercise']: 'M',
            ['default']: 'M',
            ['conversation']: 'S',
            ['exam']: 'L',
            ['inExam']: 'M',
        };
        return this.sidebarData.sidebarType ? size[this.sidebarData.sidebarType] : 'M';
    }

    openFilterExercisesDialog() {
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
            this.isFilterActive = exerciseFilterResults.isFilterActive;
        });
    }

    initializeFilterOptions() {
        if (this.exerciseFilters) {
            return;
        }

        const scoreAndPointsFilterOptions = getAchievablePointsAndAchievedScoreFilterOptions(this.sidebarData, this.exerciseFilters);

        this.exerciseFilters = {
            categoryFilter: getExerciseCategoryFilterOptions(this.sidebarData, this.exerciseFilters),
            exerciseTypesFilter: getExerciseTypeFilterOptions(this.sidebarData, this.exerciseFilters),
            difficultyFilter: getExerciseDifficultyFilterOptions(this.sidebarData, this.exerciseFilters),
            achievedScore: scoreAndPointsFilterOptions?.achievedScore,
            achievablePoints: scoreAndPointsFilterOptions?.achievablePoints,
        };
    }
}
