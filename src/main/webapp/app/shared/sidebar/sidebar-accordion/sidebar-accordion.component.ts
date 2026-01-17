import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, inject, input } from '@angular/core';
import { faChevronRight, faFile } from '@fortawesome/free-solid-svg-icons';
import { Params } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbCollapse } from '@ng-bootstrap/ng-bootstrap';
import { NgClass, TitleCasePipe } from '@angular/common';
import { SidebarCardDirective } from '../directive/sidebar-card.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { SearchFilterPipe } from 'app/shared/pipes/search-filter.pipe';
import { AccordionGroups, ChannelTypeIcons, CollapseState, SidebarCardElement, SidebarItemShowAlways, SidebarTypes } from 'app/shared/types/sidebar';
import { WeekGroup, WeekGroupingUtil } from 'app/shared/util/week-grouping.util';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { Subject, takeUntil } from 'rxjs';
import { LocalStorageService } from 'app/shared/service/local-storage.service';

@Component({
    selector: 'jhi-sidebar-accordion',
    templateUrl: './sidebar-accordion.component.html',
    styleUrls: ['./sidebar-accordion.component.scss'],
    imports: [FaIconComponent, NgbCollapse, NgClass, SidebarCardDirective, TitleCasePipe, ArtemisTranslatePipe, ArtemisDatePipe, SearchFilterPipe],
})
export class SidebarAccordionComponent implements OnChanges, OnInit, OnDestroy {
    protected readonly Object = Object;
    private metisConversationService = inject(MetisConversationService);
    private localStorageService = inject(LocalStorageService);
    private ngUnsubscribe = new Subject<void>();

    @Output() onUpdateSidebar = new EventEmitter<void>();
    @Input() searchValue: string;
    @Input() routeParams: Params;
    @Input() groupedData: AccordionGroups;
    @Input() sidebarType?: SidebarTypes;
    @Input() storageId?: string = '';
    @Input() courseId?: number;
    @Input() itemSelected?: boolean;
    @Input() showLeadingIcon = false;
    @Input() channelTypeIcon?: ChannelTypeIcons;
    sidebarItemAlwaysShow = input.required<SidebarItemShowAlways>();
    @Input() collapseState: CollapseState;
    @Input() isFilterActive = false;

    readonly faChevronRight = faChevronRight;
    readonly faFile = faFile;
    totalUnreadMessagesPerGroup: { [key: string]: number } = {};

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

    ngOnChanges() {
        if (this.searchValue || this.isFilterActive) {
            this.expandAll();
        } else {
            this.setStoredCollapseState();
        }
    }

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }

    setStoredCollapseState() {
        const storedCollapseState: CollapseState | undefined = this.localStorageService.retrieve<CollapseState>(
            'sidebar.accordion.collapseState.' + this.storageId + '.byCourse.' + this.courseId,
        );
        if (storedCollapseState) this.collapseState = storedCollapseState;
    }

    expandAll() {
        Object.keys(this.collapseState).forEach((key) => {
            this.collapseState[key] = false;
        });
    }

    expandGroupWithSelectedItem() {
        if (this.routeParams) {
            const routeParamKey = Object.keys(this.routeParams)[0];
            if (this.routeParams[routeParamKey] && this.groupedData) {
                const groupWithSelectedItem = Object.entries(this.groupedData).find((groupedItem) =>
                    groupedItem[1].entityData.some((entityItem: SidebarCardElement) => entityItem.id === Number(this.routeParams[routeParamKey])),
                );
                if (groupWithSelectedItem) {
                    const groupName = groupWithSelectedItem[0];
                    this.collapseState[groupName] = false;
                }
            }
        }
    }

    private shouldCountUnreadMessages(item: SidebarCardElement): boolean {
        return !!item.conversation?.unreadMessagesCount && item.conversation?.isMuted === false;
    }

    calculateUnreadMessagesOfGroup(): void {
        if (!this.groupedData) {
            this.totalUnreadMessagesPerGroup = {};
            return;
        }

        Object.keys(this.groupedData).forEach((groupKey) => {
            this.totalUnreadMessagesPerGroup[groupKey] = this.groupedData[groupKey].entityData
                .filter((item: SidebarCardElement) => this.shouldCountUnreadMessages(item))
                .reduce((sum, item) => sum + (item.conversation?.unreadMessagesCount || 0), 0);
        });
    }

    hasMarkedAsUnreadConversations(groupKey: string): boolean {
        return this.groupedData[groupKey].entityData.some((item: SidebarCardElement) => item.conversation?.isMarkedAsUnread);
    }

    toggleGroupCategoryCollapse(groupCategoryKey: string) {
        this.collapseState[groupCategoryKey] = !this.collapseState[groupCategoryKey];
        this.localStorageService.store<CollapseState>('sidebar.accordion.collapseState.' + this.storageId + '.byCourse.' + this.courseId, this.collapseState);
    }

    getGroupedByWeek(groupKey: string): WeekGroup[] {
        return WeekGroupingUtil.getGroupedByWeek(this.groupedData[groupKey].entityData, this.storageId, groupKey, this.searchValue);
    }
}
