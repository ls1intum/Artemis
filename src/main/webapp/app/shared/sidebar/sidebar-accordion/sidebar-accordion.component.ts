import { Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { faChevronRight, faFile } from '@fortawesome/free-solid-svg-icons';
import { AccordionGroups, ChannelAccordionShowAdd, ChannelGroupCategory, ChannelTypeIcons, CollapseState, SidebarCardElement, SidebarTypes } from 'app/types/sidebar';
import { Params } from '@angular/router';

@Component({
    selector: 'jhi-sidebar-accordion',
    templateUrl: './sidebar-accordion.component.html',
    styleUrls: ['./sidebar-accordion.component.scss'],
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
    @Input() showAddOptions = false;
    @Input() showAddOption?: ChannelAccordionShowAdd;
    @Input() channelTypeIcon?: ChannelTypeIcons;
    @Input() collapseState: CollapseState;
    @Input() isFilterActive: boolean = false;

    readonly faChevronRight = faChevronRight;
    readonly faFile = faFile;

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
    }

    setStoredCollapseState() {
        const storedCollapseState: string | null = sessionStorage.getItem('sidebar.accordion.collapseState.' + this.storageId + '.byCourse.' + this.courseId);
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

    toggleGroupCategoryCollapse(groupCategoryKey: string) {
        this.collapseState[groupCategoryKey] = !this.collapseState[groupCategoryKey];
        sessionStorage.setItem('sidebar.accordion.collapseState.' + this.storageId + '.byCourse.' + this.courseId, JSON.stringify(this.collapseState));
    }

    getGroupKey(groupKey: string): boolean {
        if (!this.showAddOption) {
            return false;
        }
        return this.showAddOption[groupKey as ChannelGroupCategory];
    }
}
