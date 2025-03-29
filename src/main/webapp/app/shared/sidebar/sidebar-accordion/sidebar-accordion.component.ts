import { Component, EventEmitter, Input, OnChanges, OnInit, Output, input } from '@angular/core';
import { faChevronRight, faFile } from '@fortawesome/free-solid-svg-icons';
import { Params } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbCollapse } from '@ng-bootstrap/ng-bootstrap';
import { NgClass, TitleCasePipe } from '@angular/common';
import { SidebarCardDirective } from '../sidebar-card.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SearchFilterPipe } from 'app/shared/pipes/search-filter.pipe';
import { AccordionGroups, ChannelTypeIcons, CollapseState, SidebarCardElement, SidebarItemShowAlways, SidebarTypes } from 'app/shared/types/sidebar';
import dayjs from 'dayjs/esm';
import isoWeek from 'dayjs/plugin/isoWeek';

dayjs.extend(isoWeek);

interface WeekGroup {
    weekRange: string;
    items: SidebarCardElement[];
}

const MIN_ITEMS_TO_GROUP_BY_WEEK = 5;

@Component({
    selector: 'jhi-sidebar-accordion',
    templateUrl: './sidebar-accordion.component.html',
    styleUrls: ['./sidebar-accordion.component.scss'],
    imports: [FaIconComponent, NgbCollapse, NgClass, SidebarCardDirective, TitleCasePipe, ArtemisTranslatePipe, SearchFilterPipe],
})
export class SidebarAccordionComponent implements OnChanges, OnInit {
    protected readonly Object = Object;

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
    }

    ngOnChanges() {
        if (this.searchValue || this.isFilterActive) {
            this.expandAll();
        } else {
            this.setStoredCollapseState();
        }
        this.calculateUnreadMessagesOfGroup();
    }

    setStoredCollapseState() {
        const storedCollapseState: string | null = localStorage.getItem('sidebar.accordion.collapseState.' + this.storageId + '.byCourse.' + this.courseId);
        if (storedCollapseState) this.collapseState = JSON.parse(storedCollapseState);
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

    calculateUnreadMessagesOfGroup(): void {
        if (!this.groupedData) {
            this.totalUnreadMessagesPerGroup = {};
            return;
        }

        Object.keys(this.groupedData).forEach((groupKey) => {
            this.totalUnreadMessagesPerGroup[groupKey] = this.groupedData[groupKey].entityData
                .filter((item: SidebarCardElement) => item.conversation?.unreadMessagesCount)
                .reduce((sum, item) => sum + (item.conversation?.unreadMessagesCount || 0), 0);
        });
    }

    toggleGroupCategoryCollapse(groupCategoryKey: string) {
        this.collapseState[groupCategoryKey] = !this.collapseState[groupCategoryKey];
        localStorage.setItem('sidebar.accordion.collapseState.' + this.storageId + '.byCourse.' + this.courseId, JSON.stringify(this.collapseState));
    }

    private getWeekKey(date: dayjs.Dayjs): string {
        const weekStart = date.startOf('isoWeek');
        const weekEnd = date.endOf('isoWeek');
        return `${weekStart.format('DD MMM')} - ${weekEnd.format('DD MMM YYYY')}`;
    }

    getGroupedByWeek(groupKey: string): WeekGroup[] {
        const items = this.groupedData[groupKey].entityData;

        // Apply search filter
        const filteredItems = this.searchValue
            ? items.filter((item) => item.title?.toLowerCase().includes(this.searchValue.toLowerCase()) || item.type?.toLowerCase().includes(this.searchValue.toLowerCase()))
            : items;

        // For exams, always return as a single group without week ranges
        if (groupKey === 'real' || groupKey === 'test' || groupKey === 'attempt') {
            return [{ weekRange: '', items: filteredItems }];
        }

        if (filteredItems.length <= MIN_ITEMS_TO_GROUP_BY_WEEK || this.searchValue) {
            return [{ weekRange: '', items: filteredItems }];
        }

        const weekGroups = new Map<string, SidebarCardElement[]>();

        for (const item of filteredItems) {
            const date = item.exercise?.dueDate || item.startDateWithTime;
            if (!date) {
                const noDateKey = 'No Date';
                const noDateGroup = weekGroups.get(noDateKey) || [];
                noDateGroup.push(item);
                weekGroups.set(noDateKey, noDateGroup);
                continue;
            }

            const weekKey = this.getWeekKey(date);
            const group = weekGroups.get(weekKey) || [];
            group.push(item);
            weekGroups.set(weekKey, group);
        }

        return Array.from(weekGroups.entries())
            .map(([weekRange, items]) => ({ weekRange, items }))
            .sort((a, b) => {
                if (a.weekRange === 'No Date') return 1;
                if (b.weekRange === 'No Date') return -1;

                const aDate = dayjs(a.weekRange.split(' - ')[0], 'DD MMM');
                const bDate = dayjs(b.weekRange.split(' - ')[0], 'DD MMM');
                return aDate.isBefore(bDate) ? -1 : 1;
            });
    }
}
