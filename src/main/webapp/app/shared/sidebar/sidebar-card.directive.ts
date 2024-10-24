import { ComponentRef, Directive, EventEmitter, Input, OnDestroy, OnInit, Output, Type, ViewContainerRef } from '@angular/core';
import { SidebarCardSmallComponent } from 'app/shared/sidebar/sidebar-card-small/sidebar-card-small.component';
import { SidebarCardMediumComponent } from 'app/shared/sidebar/sidebar-card-medium/sidebar-card-medium.component';
import { SidebarCardLargeComponent } from 'app/shared/sidebar/sidebar-card-large/sidebar-card-large.component';
import { SidebarCardElement, SidebarTypes } from 'app/types/sidebar';

@Directive({
    selector: '[jhiSidebarCard]',
    standalone: true,
})
export class SidebarCardDirective implements OnInit, OnDestroy {
    @Input() size = 'M';
    @Input() sidebarItem: SidebarCardElement;
    @Input() sidebarType?: SidebarTypes;
    @Input() itemSelected?: boolean;
    @Input() groupKey?: string;

    @Output() onUpdateSidebar = new EventEmitter<void>();

    private componentRef: ComponentRef<any>;

    constructor(public viewContainerRef: ViewContainerRef) {}

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
