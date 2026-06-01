import { ComponentRef, Directive, OnDestroy, OnInit, OutputRefSubscription, Type, ViewContainerRef, effect, inject, input, output } from '@angular/core';
import { SidebarCardSmallComponent } from 'app/course/sidebar/sidebar-card-small/sidebar-card-small.component';
import { SidebarCardMediumComponent } from 'app/course/sidebar/sidebar-card-medium/sidebar-card-medium.component';
import { SidebarCardLargeComponent } from 'app/course/sidebar/sidebar-card-large/sidebar-card-large.component';
import { SidebarCardElement, SidebarTypes } from 'app/foundation/types/sidebar';

@Directive({
    selector: '[jhiSidebarCard]',
})
export class SidebarCardDirective implements OnInit, OnDestroy {
    viewContainerRef = inject(ViewContainerRef);

    readonly size = input('M');
    readonly sidebarItem = input<SidebarCardElement>();
    readonly sidebarType = input<SidebarTypes>();
    readonly itemSelected = input<boolean>();
    readonly groupKey = input<string>();

    readonly onUpdateSidebar = output<void>();

    private componentRef: ComponentRef<SidebarCardSmallComponent | SidebarCardMediumComponent | SidebarCardLargeComponent>;
    private updateSubscription?: OutputRefSubscription;

    constructor() {
        // Re-apply inputs whenever any bound signal input changes (replaces ngOnChanges).
        effect(() => {
            // Track all inputs so the effect re-runs on any change.
            this.sidebarItem();
            this.sidebarType();
            this.itemSelected();
            this.groupKey();
            if (this.componentRef) {
                this.assignAttributes();
            }
        });
    }

    ngOnInit() {
        const cards: { [key: string]: Type<SidebarCardSmallComponent | SidebarCardMediumComponent | SidebarCardLargeComponent> } = {
            S: SidebarCardSmallComponent,
            M: SidebarCardMediumComponent,
            L: SidebarCardLargeComponent,
        };

        const cardType = cards[this.size()];
        if (cardType) {
            this.componentRef = this.viewContainerRef.createComponent(cardType);
            if (this.size() === 'S') {
                this.updateSubscription = (this.componentRef.instance as SidebarCardSmallComponent).onUpdateSidebar.subscribe(() => this.onUpdateSidebar.emit());
            }
            this.assignAttributes();
        }
    }

    ngOnDestroy() {
        this.updateSubscription?.unsubscribe();
        if (this.componentRef) {
            this.componentRef.destroy();
        }
    }

    private assignAttributes() {
        if (this.componentRef) {
            if (this.groupKey() !== undefined) {
                this.componentRef.setInput('groupKey', this.groupKey());
            }

            this.componentRef.setInput('itemSelected', this.itemSelected());
            this.componentRef.setInput('sidebarType', this.sidebarType());
            const sidebarItem = this.sidebarItem();
            if (sidebarItem) {
                // Do not mutate the signal input value; pass a shallow copy with the cleaned-up title instead.
                this.componentRef.setInput('sidebarItem', { ...sidebarItem, title: this.removeChannelPrefix(sidebarItem.title) });
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

        if (channelTypes.includes(<string>this.groupKey())) {
            prefixes.forEach((prefix) => {
                if (name?.startsWith(prefix)) {
                    name = name.substring(prefix.length);
                }
            });
        }
        return name;
    }
}
