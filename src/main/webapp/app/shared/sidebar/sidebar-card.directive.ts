import { ComponentRef, Directive, EventEmitter, Input, OnDestroy, OnInit, Output, Type, ViewContainerRef, inject } from '@angular/core';
import { SidebarCardSmallComponent } from 'app/shared/sidebar/sidebar-card-small/sidebar-card-small.component';
import { SidebarCardMediumComponent } from 'app/shared/sidebar/sidebar-card-medium/sidebar-card-medium.component';
import { SidebarCardLargeComponent } from 'app/shared/sidebar/sidebar-card-large/sidebar-card-large.component';
import { SidebarCardElement, SidebarTypes } from 'app/types/sidebar';

@Directive({
    selector: '[jhiSidebarCard]',
    standalone: true,
})
export class SidebarCardDirective implements OnInit, OnDestroy {
    viewContainerRef = inject(ViewContainerRef);

    @Input() size = 'M';
    @Input() sidebarItem: SidebarCardElement;
    @Input() sidebarType?: SidebarTypes;
    @Input() itemSelected?: boolean;

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

    ngOnDestroy() {
        if (this.componentRef) {
            this.componentRef.destroy();
        }
    }

    private assignAttributes() {
        if (this.componentRef) {
            this.componentRef.instance.itemSelected = this.itemSelected;
            this.componentRef.instance.sidebarType = this.sidebarType;
            this.componentRef.instance.sidebarItem = this.sidebarItem;
            if (this.size == 'S') {
                this.componentRef.instance.onUpdateSidebar = this.onUpdateSidebar;
            }
        }
    }
}
