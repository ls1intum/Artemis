import { ComponentRef, Directive, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, SimpleChanges, Type, ViewContainerRef, inject } from '@angular/core';
import { SidebarCardSmallComponent } from 'app/shared/sidebar/sidebar-card-small/sidebar-card-small.component';
import { SidebarCardMediumComponent } from 'app/shared/sidebar/sidebar-card-medium/sidebar-card-medium.component';
import { SidebarCardLargeComponent } from 'app/shared/sidebar/sidebar-card-large/sidebar-card-large.component';
import { SidebarCardElement, SidebarTypes } from 'app/shared/types/sidebar';

@Directive({
    selector: '[jhiSidebarCard]',
})
export class SidebarCardDirective implements OnInit, OnChanges, OnDestroy {
    viewContainerRef = inject(ViewContainerRef);

    @Input() size = 'M';
    @Input() sidebarItem: SidebarCardElement;
    @Input() sidebarType?: SidebarTypes;
    @Input() itemSelected?: boolean;
    @Input() groupKey?: string;

    @Output() onUpdateSidebar = new EventEmitter<void>();

    private componentRef: ComponentRef<any>;

    ngOnInit() {
        const cards: { [key: string]: Type<SidebarCardSmallComponent | SidebarCardMediumComponent | SidebarCardLargeComponent> } = {
            S: SidebarCardSmallComponent,
            M: SidebarCardMediumComponent,
            L: SidebarCardLargeComponent,
        };

        const cardType = cards[this.size];
        if (cardType) {
            this.componentRef = this.viewContainerRef.createComponent(cardType);
            this.assignAttributes();
        }
    }

    ngOnChanges(changes: SimpleChanges) {
        if (this.componentRef) {
            this.assignAttributes();
        }
    }

    ngOnDestroy() {
        if (this.componentRef) {
            this.componentRef.destroy();
        }
    }

    private assignAttributes() {
        if (this.componentRef) {
            if (this.groupKey !== undefined) {
                this.componentRef.instance.groupKey = this.groupKey;
            }

            this.componentRef.instance.itemSelected = this.itemSelected;
            this.componentRef.instance.sidebarType = this.sidebarType;
            this.componentRef.instance.sidebarItem = this.sidebarItem;
            this.componentRef.instance.sidebarItem.title = this.removeChannelPrefix(this.sidebarItem.title);

            if (this.size == 'S') {
                this.componentRef.instance.onUpdateSidebar = this.onUpdateSidebar;
            }
        }
    }

    /**
     * Removes known channel prefixes (e.g. 'exercise-', 'lecture-', 'exam-') from the given title,
     * but only if the current item belongs to one of the corresponding channel groups.
     *
     * This is used to clean up channel names displayed in the sidebar by stripping technical prefixes.
     *
     * @param name The original channel name (e.g. 'exercise-Homework 1')
     * @returns The cleaned-up name (e.g. 'Homework 1')
     */
    removeChannelPrefix(name: string): string {
        const prefixes = ['exercise-', 'lecture-', 'exam-'];
        const channelTypes = ['exerciseChannels', 'lectureChannels', 'examChannels'];

        if (channelTypes.includes(<string>this.groupKey)) {
            prefixes.forEach((prefix) => {
                if (name?.startsWith(prefix)) {
                    name = name.substring(prefix.length);
                }
            });
        }
        return name;
    }
}
