import { Component, computed, input, output } from '@angular/core';
import { SidebarCardSmallComponent } from 'app/course/sidebar/sidebar-card-small/sidebar-card-small.component';
import { SidebarCardMediumComponent } from 'app/course/sidebar/sidebar-card-medium/sidebar-card-medium.component';
import { SidebarCardLargeComponent } from 'app/course/sidebar/sidebar-card-large/sidebar-card-large.component';
import { SidebarCardElement, SidebarCardSize, SidebarTypes } from 'app/foundation/types/sidebar';
import { cloneWith } from 'app/foundation/util/deep-clone.util';

/**
 * Renders a sidebar item as the card of its size.
 *
 * The three cards are declared in the template instead of being created in code, so the template compiler checks every
 * input that is passed to them. Only the medium card knows which entity the detail route shows, and only the small card
 * reports that the sidebar has to be reloaded; both are bound where they exist.
 *
 * Like every component in this application it is `OnPush`, which is the default since Angular 22. The card gets a new copy
 * of the item whenever the item, its group or the selection state changes, as it did when a directive created the card.
 * Everything a card derives from the item is recomputed then. The conversation service updates a conversation in place
 * (unread count, mute and favorite flags), and the copy carries the live conversation (see {@link cardItem}), so a template
 * expression shows that state whenever the card is checked. State that a card derives once from the conversation, such as
 * the mute flag, is refreshed when the sidebar is rebuilt, which the conversation options trigger.
 */
@Component({
    selector: 'jhi-sidebar-card',
    templateUrl: './sidebar-card.component.html',
    // The host only wraps the card, so it must not take part in the layout of the list that the card sits in.
    styles: [':host { display: contents; }'],
    imports: [SidebarCardSmallComponent, SidebarCardMediumComponent, SidebarCardLargeComponent],
})
export class SidebarCardComponent {
    readonly size = input<SidebarCardSize>('M');
    readonly sidebarItem = input<SidebarCardElement>();
    readonly sidebarType = input<SidebarTypes>();
    readonly itemSelected = input<boolean>();
    readonly groupKey = input<string>();
    /** Id of the entity the detail route currently shows; only the medium card declares a matching input. */
    readonly activeItemId = input<number>();

    readonly onUpdateSidebar = output<void>();

    /**
     * The item as the card displays it: a copy with the cleaned-up title, so the signal input value is not mutated.
     * The copy must carry the live conversation rather than a clone of it: the conversation service and the conversation
     * options update it in place (unread count and marker, mute, favorite and hidden flags), and a private snapshot would
     * keep showing the state from when the sidebar was built. Override values are taken by reference.
     */
    protected readonly cardItem = computed(() => {
        const sidebarItem = this.sidebarItem();
        // Read only to hand the card a new copy when the selection state changes, as the directive did on every input change:
        // the copy deep-clones the exercise, participation or exam of the item, so a card sees their current state again.
        this.itemSelected();
        this.sidebarType();
        this.activeItemId();
        if (!sidebarItem) {
            return undefined;
        }
        const title = this.removeChannelPrefix(sidebarItem.title);
        return cloneWith(sidebarItem, sidebarItem.conversation ? { title, conversation: sidebarItem.conversation } : { title });
    });

    /**
     * Removes known channel prefixes (e.g. 'exercise-', 'lecture-', 'exam-') from the given title,
     * but only if the current item belongs to one of the corresponding channel groups.
     *
     * This is used to clean up channel names displayed in the sidebar by stripping technical prefixes.
     *
     * @param name The original channel name (e.g. 'exercise-Homework 1')
     * @returns The cleaned-up name (e.g. 'Homework 1')
     */
    private removeChannelPrefix(name: string): string {
        const prefixes = ['exercise-', 'lecture-', 'exam-'];
        const channelTypes = ['exerciseChannels', 'lectureChannels', 'examChannels'];

        if (channelTypes.includes(this.groupKey() as string)) {
            prefixes.forEach((prefix) => {
                if (name?.startsWith(prefix)) {
                    name = name.substring(prefix.length);
                }
            });
        }
        return name;
    }
}
