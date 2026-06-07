import { Component, OnDestroy, effect, inject, input, output, signal } from '@angular/core';
import { faCheckDouble, faFilter, faFilterCircleXmark, faHashtag, faPeopleGroup, faPlusCircle, faSearch, faUser } from '@fortawesome/free-solid-svg-icons';
import { ActivatedRoute, Params } from '@angular/router';
import { Subscription, distinctUntilChanged } from 'rxjs';
import { SidebarEventService } from './service/sidebar-event.service';
import { NgbDropdown, NgbDropdownButtonItem, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { cloneDeep } from 'lodash-es';
import { ExerciseFilterOptions, ExerciseFilterResults } from 'app/foundation/types/exercise-filter';
import {
    getAchievablePointsAndAchievedScoreFilterOptions,
    getExerciseCategoryFilterOptions,
    getExerciseDifficultyFilterOptions,
    getExerciseTypeFilterOptions,
} from 'app/course/sidebar/sidebar.helper';
import { ExerciseFilterModalComponent } from 'app/exercise/exercise-filter/exercise-filter-modal.component';
import { NgClass } from '@angular/common';
import { SearchFilterComponent } from 'app/shared-ui/search-filter/search-filter.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { SidebarAccordionComponent } from './sidebar-accordion/sidebar-accordion.component';
import { SidebarCardDirective } from './directive/sidebar-card.directive';
import { SearchFilterPipe } from 'app/foundation/pipes/search-filter.pipe';
import { ChannelTypeIcons, CollapseState, SidebarCardSize, SidebarData, SidebarItemShowAlways, SidebarTypes } from 'app/foundation/types/sidebar';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';

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
export class SidebarComponent implements OnDestroy {
    private route = inject(ActivatedRoute);
    private sidebarEventService = inject(SidebarEventService);
    private modalService = inject(NgbModal);
    private sessionStorageService = inject(SessionStorageService);

    readonly onSelectConversation = output<number | string>();
    readonly onUpdateSidebar = output<void>();
    onDirectChatPressed = output<void>();
    onGroupChatPressed = output<void>();
    onBrowsePressed = output<void>();
    onCreateChannelPressed = output<void>();
    onMarkAllChannelsAsRead = output<void>();
    readonly searchFieldEnabled = input<boolean>(true);
    readonly sidebarData = input.required<SidebarData>();
    readonly courseId = input<number>();
    readonly itemSelected = input<boolean>();
    readonly channelTypeIcon = input<ChannelTypeIcons>();
    readonly collapseState = input.required<CollapseState>();
    sidebarItemAlwaysShow = input.required<SidebarItemShowAlways>();
    readonly showFilter = input<boolean>(false);
    inCommunication = input<boolean>(false);
    readonly searchValue = signal<string>('');
    isCollapsed = false;
    readonly reEmitNonDistinctSidebarEvents = input<boolean>(false);

    /** Working copy of the sidebar data, seeded from the {@link sidebarData} input. It is replaced locally when
     *  the user applies exercise filters, without mutating the parent-owned input. */
    readonly sidebarDataInternal = signal<SidebarData>({} as SidebarData);

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

    readonly sidebarDataBeforeFiltering = signal<SidebarData | undefined>(undefined);

    readonly exerciseFilters = signal<ExerciseFilterOptions | undefined>(undefined);
    readonly isFilterActive = signal<boolean>(false);

    constructor() {
        // Seed the working sidebar data from the input.
        effect(() => {
            this.sidebarDataInternal.set(this.sidebarData());
        });
        effect(() => {
            this.subscribeToSidebarEvents();
        });
        // Replaces ngOnChanges: (re)subscribe to route params.
        effect(() => {
            // Re-run when the route changes (the ActivatedRoute itself is stable, but keep parity with the
            // previous ngOnChanges trigger which fired on input changes).
            this.sidebarData();
            this.paramSubscription?.unsubscribe();
            this.paramSubscription = this.route.params?.subscribe((params) => {
                this.routeParams = params;
            });
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
        this.sidebarEventSubscription = pipe.subscribe((targetComponentRoute) => {
            if (targetComponentRoute) {
                this.storeLastSelectedItemTargetComponentRoute(targetComponentRoute);
                if (this.sidebarDataInternal().sidebarType == 'conversation') {
                    this.onSelectConversation.emit(targetComponentRoute);
                }
            }
        });
    }

    setSearchValue(searchValue: string) {
        this.searchValue.set(searchValue);
    }

    storeLastSelectedItemTargetComponentRoute(targetComponentRoute: number | string) {
        this.sessionStorageService.store('sidebar.lastSelectedItem.' + this.sidebarDataInternal().storageId + '.byCourse.' + this.courseId(), targetComponentRoute);
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
        const sidebarType = this.sidebarDataInternal().sidebarType;
        return sidebarType ? size[sidebarType] : 'M';
    }

    // NOTE: This dialog still uses NgbModal because it opens ExerciseFilterModalComponent, which lives in the
    // exercise/** carve-out (NgbActiveModal + componentInstance contract). Migrating it to PrimeNG DialogService
    // would require changing that carve-out component, so it is deferred until the carve-out is migrated.
    openFilterExercisesDialog() {
        this.initializeFilterOptions();

        if (!this.sidebarDataBeforeFiltering()) {
            this.sidebarDataBeforeFiltering.set(cloneDeep(this.sidebarDataInternal()));
        }

        this.modalRef = this.modalService.open(ExerciseFilterModalComponent, {
            size: 'lg',
            backdrop: 'static',
            animation: true,
        });

        this.modalRef.componentInstance.sidebarData = cloneDeep(this.sidebarDataBeforeFiltering());
        this.modalRef.componentInstance.exerciseFilters = cloneDeep(this.exerciseFilters());

        this.modalRef.componentInstance.filterApplied.subscribe((exerciseFilterResults: ExerciseFilterResults) => {
            this.sidebarDataInternal.set(exerciseFilterResults.filteredSidebarData!);
            this.exerciseFilters.set(exerciseFilterResults.appliedExerciseFilters);
            this.isFilterActive.set(exerciseFilterResults.isFilterActive);
        });
    }

    initializeFilterOptions() {
        if (this.exerciseFilters()) {
            return;
        }

        const sidebarData = this.sidebarDataInternal();
        const scoreAndPointsFilterOptions = getAchievablePointsAndAchievedScoreFilterOptions(sidebarData, this.exerciseFilters());

        this.exerciseFilters.set({
            categoryFilter: getExerciseCategoryFilterOptions(sidebarData, this.exerciseFilters()),
            exerciseTypesFilter: getExerciseTypeFilterOptions(sidebarData, this.exerciseFilters()),
            difficultyFilter: getExerciseDifficultyFilterOptions(sidebarData, this.exerciseFilters()),
            achievedScore: scoreAndPointsFilterOptions?.achievedScore,
            achievablePoints: scoreAndPointsFilterOptions?.achievablePoints,
        });
    }

    markAllMessagesAsChecked() {
        this.onMarkAllChannelsAsRead.emit();
    }
}
