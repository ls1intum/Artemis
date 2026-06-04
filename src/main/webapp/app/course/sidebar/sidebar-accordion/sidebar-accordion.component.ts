import { Component, OnDestroy, OnInit, effect, inject, input, output, signal, untracked } from '@angular/core';
import { faChevronRight, faFile } from '@fortawesome/free-solid-svg-icons';
import { Params } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbCollapse } from '@ng-bootstrap/ng-bootstrap';
import { NgClass, TitleCasePipe } from '@angular/common';
import { SidebarCardDirective } from '../directive/sidebar-card.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { SearchFilterPipe } from 'app/foundation/pipes/search-filter.pipe';
import { AccordionGroups, ChannelTypeIcons, CollapseState, SidebarCardElement, SidebarItemShowAlways, SidebarTypes } from 'app/foundation/types/sidebar';
import { WeekGroup, WeekGroupingUtil } from 'app/foundation/util/week-grouping.util';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { Subject, takeUntil } from 'rxjs';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';

@Component({
    selector: 'jhi-sidebar-accordion',
    templateUrl: './sidebar-accordion.component.html',
    styleUrls: ['./sidebar-accordion.component.scss'],
    imports: [FaIconComponent, NgbCollapse, NgClass, SidebarCardDirective, TitleCasePipe, ArtemisTranslatePipe, ArtemisDatePipe, SearchFilterPipe],
})
export class SidebarAccordionComponent implements OnInit, OnDestroy {
    protected readonly Object = Object;
    private metisConversationService = inject(MetisConversationService);
    private localStorageService = inject(LocalStorageService);
    private ngUnsubscribe = new Subject<void>();

    readonly onUpdateSidebar = output<void>();
    readonly searchValue = input<string>('');
    readonly routeParams = input<Params>();
    readonly groupedData = input.required<AccordionGroups>();
    readonly sidebarType = input<SidebarTypes>();
    readonly storageId = input<string>('');
    readonly courseId = input<number>();
    readonly itemSelected = input<boolean>();
    readonly showLeadingIcon = input<boolean>(false);
    readonly channelTypeIcon = input<ChannelTypeIcons>();
    sidebarItemAlwaysShow = input.required<SidebarItemShowAlways>();
    readonly collapseState = input.required<CollapseState>();
    readonly isFilterActive = input<boolean>(false);

    /** Working copy of the collapse state. Seeded by reference from the {@link collapseState} input so in-place
     *  property mutations remain visible to the parent, but can be replaced when a stored state is restored. */
    readonly collapseStateInternal = signal<CollapseState>({} as CollapseState);

    readonly faChevronRight = faChevronRight;
    readonly faFile = faFile;
    totalUnreadMessagesPerGroup: { [key: string]: number } = {};

    constructor() {
        // Seed the working collapse state from the input.
        effect(() => {
            this.collapseStateInternal.set(this.collapseState());
        });
        // Replaces ngOnChanges: react to search/filter changes. Only the trigger inputs are tracked; the body
        // reads and writes collapseStateInternal, so it must run untracked to avoid re-triggering this effect
        // (expandAll/setStoredCollapseState write a new collapse-state object on every run, which would otherwise
        // create an infinite reactive loop).
        effect(() => {
            const shouldExpandAll = !!this.searchValue() || this.isFilterActive();
            // Track the storage-key inputs too: mirroring the former ngOnChanges, a change of course/storage key must
            // reload the stored collapse state for that key (setStoredCollapseState reads them inside untracked()).
            this.courseId();
            this.storageId();
            untracked(() => {
                if (shouldExpandAll) {
                    this.expandAll();
                } else {
                    this.setStoredCollapseState();
                }
            });
        });
    }

    ngOnInit() {
        this.expandGroupWithSelectedItem();
        this.setStoredCollapseState();
        this.metisConversationService.conversationsOfUser$.pipe(takeUntil(this.ngUnsubscribe)).subscribe((c) => {
            setTimeout(() => {
                this.calculateUnreadMessagesOfGroup();
            }, 0);
        });
        this.metisConversationService.activeConversation$.pipe(takeUntil(this.ngUnsubscribe)).subscribe(() => {
            setTimeout(() => {
                this.calculateUnreadMessagesOfGroup();
            }, 0);
        });
    }

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }

    setStoredCollapseState() {
        const storedCollapseState: CollapseState | undefined = this.localStorageService.retrieve<CollapseState>(
            'sidebar.accordion.collapseState.' + this.storageId() + '.byCourse.' + this.courseId(),
        );
        if (storedCollapseState) this.collapseStateInternal.set(storedCollapseState);
    }

    expandAll() {
        const collapseState = { ...this.collapseStateInternal() };
        Object.keys(collapseState).forEach((key) => {
            collapseState[key] = false;
        });
        this.collapseStateInternal.set(collapseState);
    }

    expandGroupWithSelectedItem() {
        const routeParams = this.routeParams();
        const groupedData = this.groupedData();
        if (routeParams) {
            const routeParamKey = Object.keys(routeParams)[0];
            if (routeParams[routeParamKey] && groupedData) {
                const groupWithSelectedItem = Object.entries(groupedData).find((groupedItem) =>
                    groupedItem[1].entityData.some((entityItem: SidebarCardElement) => entityItem.id === Number(routeParams[routeParamKey])),
                );
                if (groupWithSelectedItem) {
                    const groupName = groupWithSelectedItem[0];
                    this.collapseStateInternal.set({ ...this.collapseStateInternal(), [groupName]: false });
                }
            }
        }
    }

    private shouldCountUnreadMessages(item: SidebarCardElement): boolean {
        return !!item.conversation?.unreadMessagesCount && item.conversation?.isMuted === false;
    }

    calculateUnreadMessagesOfGroup(): void {
        const groupedData = this.groupedData();
        if (!groupedData) {
            this.totalUnreadMessagesPerGroup = {};
            return;
        }

        Object.keys(groupedData).forEach((groupKey) => {
            this.totalUnreadMessagesPerGroup[groupKey] = groupedData[groupKey].entityData
                .filter((item: SidebarCardElement) => this.shouldCountUnreadMessages(item))
                .reduce((sum, item) => sum + (item.conversation?.unreadMessagesCount || 0), 0);
        });
    }

    toggleGroupCategoryCollapse(groupCategoryKey: string) {
        const collapseState = { ...this.collapseStateInternal(), [groupCategoryKey]: !this.collapseStateInternal()[groupCategoryKey] };
        this.collapseStateInternal.set(collapseState);
        this.localStorageService.store<CollapseState>('sidebar.accordion.collapseState.' + this.storageId() + '.byCourse.' + this.courseId(), collapseState);
    }

    getGroupedByWeek(groupKey: string): WeekGroup[] {
        return WeekGroupingUtil.getGroupedByWeek(this.groupedData()[groupKey].entityData, this.storageId(), groupKey, this.searchValue());
    }
}
