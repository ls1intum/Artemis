import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, effect, inject, input, model, untracked } from '@angular/core';
import { Tab } from '@angular/aria/tabs';
import { TumAetUiTabsService, tabKey } from './tumaet-ui-tabs.service';

/**
 * Selectable tab associated with the panel that has the same value.
 *
 * An Angular Aria tab: it owns the `tab` role, `aria-selected`, `aria-controls`, `aria-disabled`, and the roving
 * `tabindex`. A disabled tab stays focusable with the arrow keys and is announced as unavailable, but cannot be selected.
 */
@Component({
    selector: 'tumaet-ui-tab',
    template: '<ng-content />',
    styleUrl: './tumaet-ui-tab.component.scss',
    host: {
        // Lets the Angular Aria test harnesses (`@angular/aria/tabs/testing`) find the tab.
        ngTab: '',
        '[class]': 'hostClasses()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiTabComponent extends Tab implements OnInit, OnDestroy {
    private readonly tabsService = inject(TumAetUiTabsService);
    private removeFromTabs?: () => void;

    /** Value that associates this tab with a tab panel. */
    // eslint-disable-next-line @angular-eslint/no-input-rename -- the public name must stay `value`; see `value` below.
    readonly tabValue = input.required<number | string>({ alias: 'value' });

    /**
     * The key aria identifies this tab by: `tabValue` passed through {@link tabKey}, so `1` and `'1'` stay two tabs.
     *
     * Aria keys a tab by the string in its `value` input, while this tab accepts numbers as well. Overriding `value` with
     * a wider input type would break the type of the aria class for every consumer that checks library types, so this
     * override keeps aria's type and moves the input to an internal name that nobody binds; the tab sets it itself.
     *
     * The override relies on how aria reads the input. Aria builds its tab pattern in a field initializer from a copy of
     * `this`, which still holds aria's own, never bound `value` input, and reads `value` only lazily through
     * `this.value()`, in the tab and panel maps and when looking up the selected tab. If an aria update starts reading
     * `value` from that copy, the tabs lose their panels and selection.
     */
    override readonly value = model('', { alias: 'tumAetUiTabKey' });

    constructor() {
        super();
        effect(() => {
            const key = tabKey(this.tabValue());
            untracked(() => this.value.set(key));
        });
    }

    protected readonly hostClasses = computed(() => {
        const state = this.selected() ? 'tumaet:text-accent' : 'tumaet:text-muted tumaet:hover:text-text';
        const disabled = this.disabled() ? 'tumaet-ui-tab-disabled' : '';
        return `tumaet-ui-tab tumaet:focus-visible:outline tumaet:focus-visible:outline-2 tumaet:focus-visible:outline-focus ${state} ${disabled}`.trim();
    });

    override ngOnInit(): void {
        // Bindings are applied by now, so aria sees the key from its first read on; the effect follows later changes.
        this.value.set(tabKey(this.tabValue()));
        super.ngOnInit();
        this.removeFromTabs = this.tabsService.addTab({ key: this.value, element: this.element, disabled: this.disabled, selected: this.selected });
    }

    override ngOnDestroy(): void {
        this.removeFromTabs?.();
        super.ngOnDestroy();
    }
}
