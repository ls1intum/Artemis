import { ChangeDetectionStrategy, Component, ElementRef, computed, inject, input } from '@angular/core';
import { TumUiTabsService } from 'app/shared-ui/tum-ui/tabs/tum-ui-tabs.service';

/**
 * A single tab header, part of the tum-aet-ui kit. Drop-in replacement for PrimeNG's `p-tab`.
 *
 * Renders a `role="tab"` element that activates its `value` on click, reflects the active state via
 * `aria-selected` + the Aura primary underline, and participates in the tab list's roving-tabindex
 * keyboard navigation (only the active tab is in the tab order). Its `id` and `aria-controls` are wired
 * to the matching `<tum-ui-tab-panel>` through the shared {@link TumUiTabsService}.
 */
@Component({
    selector: 'tum-ui-tab',
    template: '<ng-content />',
    styleUrl: './tum-ui-tab.component.scss',
    host: {
        role: 'tab',
        '[class]': 'hostClasses()',
        '[id]': 'id()',
        '[attr.aria-selected]': 'active()',
        '[attr.aria-controls]': 'panelId()',
        '[attr.aria-disabled]': 'disabled() || undefined',
        '[attr.tabindex]': 'active() ? 0 : -1',
        '(click)': 'onClick()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiTabComponent {
    private readonly tabsService = inject(TumUiTabsService);
    /** Host element, exposed so the tab list can measure the active-bar and move focus during keyboard nav. */
    readonly elementRef = inject<ElementRef<HTMLElement>>(ElementRef);

    /** This tab's key; must match a `<tum-ui-tab-panel>`'s `value`. Numbers and strings are both supported. */
    readonly value = input.required<number | string>();
    readonly disabled = input(false);

    protected readonly active = computed(() => this.tabsService.active() === this.value());
    protected readonly id = computed(() => this.tabsService.tabId(this.value()));
    protected readonly panelId = computed(() => this.tabsService.panelId(this.value()));

    // Structure/spacing live in the stylesheet; colors ride semantic Tailwind tokens so dark mode is free.
    // The bottom border is content-colored by default (Tailwind base layer) and turns primary when active,
    // reproducing the Aura `tab.activeBorderColor` inkbar edge. Focus ring uses the primary outline.
    protected readonly hostClasses = computed(() => {
        const state = this.active() ? 'text-primary border-b-primary' : 'text-muted-color hover:text-color';
        const disabled = this.disabled() ? 'tum-ui-tab-disabled' : '';
        return `tum-ui-tab focus-visible:outline focus-visible:outline-2 focus-visible:outline-primary ${state} ${disabled}`.trim();
    });

    protected onClick(): void {
        if (!this.disabled()) {
            this.tabsService.select(this.value());
        }
    }
}
