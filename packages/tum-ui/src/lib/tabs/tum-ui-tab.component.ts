import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, input } from '@angular/core';
import { Tab } from '@angular/aria/tabs';
import { TumUiTabsService, tabKey } from './tum-ui-tabs.service';

/**
 * Selectable tab associated with the panel that has the same value.
 *
 * An Angular Aria tab: it owns the `tab` role, `aria-selected`, `aria-controls`, `aria-disabled`, and the roving
 * `tabindex`. A disabled tab stays focusable with the arrow keys and is announced as unavailable, but cannot be selected.
 */
@Component({
    selector: 'tum-ui-tab',
    template: '<ng-content />',
    styleUrl: './tum-ui-tab.component.scss',
    host: {
        // Lets the Angular Aria test harnesses (`@angular/aria/tabs/testing`) find the tab.
        ngTab: '',
        '[class]': 'hostClasses()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiTabComponent extends Tab implements OnInit, OnDestroy {
    private readonly tabsService = inject(TumUiTabsService);
    private removeFromTabs?: () => void;

    /**
     * Value that associates this tab with a tab panel.
     *
     * Aria identifies a tab by a string. This input also accepts a number and hands aria the typed key from
     * {@link tabKey}, so `1` and `'1'` stay two tabs. TypeScript rejects the wider input type of the override, although
     * Angular's template type checker and runtime both use it; every aria read of `value` goes through the key.
     */
    // @ts-expect-error -- the override accepts numbers too and narrows them to aria's string key, see above.
    override readonly value = input.required<string, number | string>({ transform: tabKey });

    protected readonly hostClasses = computed(() => {
        const state = this.selected() ? 'tum:text-accent' : 'tum:text-muted tum:hover:text-text';
        const disabled = this.disabled() ? 'tum-ui-tab-disabled' : '';
        return `tum-ui-tab tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus ${state} ${disabled}`.trim();
    });

    override ngOnInit(): void {
        super.ngOnInit();
        this.removeFromTabs = this.tabsService.addTab({ key: this.value, element: this.element, disabled: this.disabled, selected: this.selected });
    }

    override ngOnDestroy(): void {
        this.removeFromTabs?.();
        super.ngOnDestroy();
    }
}
