import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, Renderer2, contentChildren, inject } from '@angular/core';
import { Menu } from '@angular/aria/menu';
import { TUM_AET_UI_MENU_TRIGGER } from './tumaet-ui-menu-trigger.directive';
import { TumAetUiMenuItemDirective } from './tumaet-ui-menu-item.directive';

/**
 * Menu surface for a list of actions, opened by {@link TumAetUiMenuTriggerDirective} and filled with
 * `[tumAetUiMenuItem]` entries.
 *
 * The roles, roving focus, arrow-key and Home/End navigation, typeahead, and closing on Escape or once an entry runs
 * come from the Angular Aria menu; this component owns the surface styling. Declare it inside the `ng-template` the
 * trigger points at, so nothing renders until the menu opens:
 *
 * ```html
 * <button [tumAetUiMenuTrigger]="actions">Actions</button>
 * <ng-template #actions>
 *     <tumaet-ui-menu>
 *         <a tumAetUiMenuItem routerLink="./students">Add students</a>
 *         <button tumAetUiMenuItem (triggered)="archive()">Archive</button>
 *     </tumaet-ui-menu>
 * </ng-template>
 * ```
 *
 * Use `tumaet-ui-popover` instead for rich or non-action content: a menu is for commands and navigation.
 */
@Component({
    selector: 'tumaet-ui-menu',
    template: '<ng-content />',
    styleUrl: './tumaet-ui-menu.component.scss',
    hostDirectives: [Menu],
    host: {
        // Lets the Angular Aria test harnesses (`@angular/aria/menu/testing`) find the menu.
        ngMenu: '',
        class: 'tumaet-ui-menu tumaet:flex tumaet:min-w-48 tumaet:flex-col tumaet:rounded-md tumaet:border tumaet:border-border tumaet:bg-overlay-background tumaet:py-1 tumaet:text-text tumaet:shadow-md',
        '(keydown)': 'onKeydown($event)',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiMenuComponent {
    private readonly menu = inject<Menu<unknown>>(Menu);
    private readonly trigger = inject(TUM_AET_UI_MENU_TRIGGER, { optional: true });
    private readonly items = contentChildren(TumAetUiMenuItemDirective, { descendants: true });

    /** The entry aria chooses if the event being dispatched selects one. */
    private pendingItem?: TumAetUiMenuItemDirective;

    constructor() {
        const element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
        const renderer = inject(Renderer2);
        const detach = this.trigger?.attachMenu(this.menu);
        // Aria reports a chosen entry through the menu's `itemSelected` output, with the entry's `value`. Entries have no
        // value here, so the entry is noted before aria acts, by a capturing listener that runs before aria's own: for a
        // click the entry that was clicked, for a key press the active entry, which is the one aria chooses even when
        // focus sits on the menu surface itself. Aria clears the active entry while closing, before it reports the choice.
        const noteTarget = (event: Event) => {
            if (event instanceof KeyboardEvent) {
                this.pendingItem = this.items().find((item) => item.isActive());
                if (event.key.length === 1) {
                    // Aria's typeahead matches the entries' search terms, which follow the rendered label.
                    this.items().forEach((item) => item.syncSearchTerm());
                }
            } else {
                this.pendingItem = this.items().find((item) => item.contains(event.target));
            }
        };
        const removeListeners = [renderer.listen(element, 'click', noteTarget, { capture: true }), renderer.listen(element, 'keydown', noteTarget, { capture: true })];
        this.menu.itemSelected.subscribe(() => {
            const item = this.pendingItem;
            this.pendingItem = undefined;
            item?.emitTriggered();
        });
        inject(DestroyRef).onDestroy(() => {
            detach?.();
            removeListeners.forEach((remove) => remove());
        });
    }

    protected onKeydown(event: KeyboardEvent): void {
        // Tab leaves the menu. The overlay sits at the end of the document, so without this the browser would move focus
        // out of the page; closing first lets it continue from the trigger, as it does for a native select.
        if (event.key === 'Tab' && !event.altKey && !event.ctrlKey && !event.metaKey) {
            this.trigger?.closeAndFocus();
        }
    }
}
