import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output, effect, inject, input, output } from '@angular/core';
import { faCheckDouble, faFilter, faFilterCircleXmark, faHashtag, faPeopleGroup, faPlusCircle, faSearch, faUser } from '@fortawesome/free-solid-svg-icons';
import { ActivatedRoute, Params } from '@angular/router';
import { Subscription, distinctUntilChanged } from 'rxjs';
import { SidebarEventService } from './service/sidebar-event.service';
import { NgbDropdown, NgbDropdownButtonItem, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { cloneDeep } from 'lodash-es';
import { ExerciseFilterOptions, ExerciseFilterResults } from 'app/shared/types/exercise-filter';
import {
    getAchievablePointsAndAchievedScoreFilterOptions,
    getExerciseCategoryFilterOptions,
    getExerciseDifficultyFilterOptions,
    getExerciseTypeFilterOptions,
} from 'app/shared/sidebar/sidebar.helper';
import { ExerciseFilterModalComponent } from 'app/shared/exercise-filter/exercise-filter-modal.component';
import { NgClass } from '@angular/common';
import { SearchFilterComponent } from '../search-filter/search-filter.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from '../language/translate.directive';
import { SidebarAccordionComponent } from './sidebar-accordion/sidebar-accordion.component';
import { SidebarCardDirective } from './directive/sidebar-card.directive';
import { SearchFilterPipe } from 'app/shared/pipes/search-filter.pipe';
import { ChannelTypeIcons, CollapseState, SidebarCardSize, SidebarData, SidebarItemShowAlways, SidebarTypes } from 'app/shared/types/sidebar';
import { SessionStorageService } from 'app/shared/service/session-storage.service';

@Component({
    selector: 'jhi-sidebar',
    templateUrl: './sidebar.component.html',
    styleUrls: ['./sidebar.component.scss'],
    imports: [
        NgClass,
        SearchFilterComponent,
        FaIconComponent,
        TranslateDirective,
        NgbDropdown,
        NgbDropdownToggle,
        NgbDropdownMenu,
        NgbDropdownButtonItem,
        NgbDropdownItem,
        SidebarAccordionComponent,
        SidebarCardDirective,
        SearchFilterPipe,
    ],
})
export class SidebarComponent implements OnDestroy, OnChanges {
    private route = inject(ActivatedRoute);
    private sidebarEventService = inject(SidebarEventService);
    private modalService = inject(NgbModal);
    private sessionStorageService = inject(SessionStorageService);

    @Output() onSelectConversation = new EventEmitter<number | string>();
    @Output() onUpdateSidebar = new EventEmitter<void>();
    onDirectChatPressed = output<void>();
    onGroupChatPressed = output<void>();
    onBrowsePressed = output<void>();
    onCreateChannelPressed = output<void>();
    onMarkAllChannelsAsRead = output<void>();
    @Input() searchFieldEnabled = true;
    @Input() sidebarData: SidebarData;
    @Input() courseId?: number;
    @Input() itemSelected?: boolean;
    @Input() channelTypeIcon?: ChannelTypeIcons;
    @Input() collapseState: CollapseState;
    sidebarItemAlwaysShow = input.required<SidebarItemShowAlways>();
    @Input() showFilter = false;
    inCommunication = input<boolean>(false);
    searchValue = '';
    isCollapsed = false;
    readonly reEmitNonDistinctSidebarEvents = input<boolean>(false);

    exerciseId: string;

    paramSubscription?: Subscription;
    sidebarEventSubscription?: Subscription;

    routeParams: Params;
    private modalRef?: NgbModalRef;

    readonly faFilter = faFilter;
    readonly faFilterCurrentlyApplied = faFilterCircleXmark;
    readonly faUser = faUser;
    readonly faPeopleGroup = faPeopleGroup;
    readonly faPlusCircle = faPlusCircle;
    readonly faSearch = faSearch;
    readonly faHashtag = faHashtag;
    readonly faCheckDouble = faCheckDouble;

    sidebarDataBeforeFiltering: SidebarData;

    exerciseFilters?: ExerciseFilterOptions;
    isFilterActive = false;

    constructor() {
        effect(() => {
            this.subscribeToSidebarEvents();
        });
    }

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

    private subscribeToSidebarEvents() {
        this.sidebarEventSubscription?.unsubscribe();
        const listener = this.sidebarEventService.sidebarCardEventListener();
        let pipe;
        if (this.reEmitNonDistinctSidebarEvents()) {
            pipe = listener;
        } else {
            pipe = listener.pipe(distinctUntilChanged());
        }
        this.sidebarEventSubscription = pipe.subscribe((itemId) => {
            if (itemId) {
                this.storeLastSelectedItem(itemId);
                if (this.sidebarData.sidebarType == 'conversation') {
                    this.onSelectConversation.emit(itemId);
                }
            }
        });
    }

    ngOnChanges() {
        this.paramSubscription?.unsubscribe();
        this.paramSubscription = this.route.params?.subscribe((params) => {
            this.routeParams = params;
        });
    }

    setSearchValue(searchValue: string) {
        this.searchValue = searchValue;
    }

    storeLastSelectedItem(itemId: number | string) {
        this.sessionStorageService.store('sidebar.lastSelectedItem.' + this.sidebarData.storageId + '.byCourse.' + this.courseId, itemId);
    }

    ngOnDestroy() {
        this.paramSubscription?.unsubscribe();
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

    markAllMessagesAsChecked() {
        this.onMarkAllChannelsAsRead.emit();
    }
}
