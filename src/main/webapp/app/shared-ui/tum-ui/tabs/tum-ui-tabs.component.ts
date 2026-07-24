import { ChangeDetectionStrategy, Component, inject, model } from '@angular/core';
import { TumUiTabsService } from 'app/shared-ui/tum-ui/tabs/tum-ui-tabs.service';

/**
 * Owned tabs container, part of the tum-aet-ui kit (future `@tumaet/ui-angular`).
 *
 * Drop-in replacement for PrimeNG's `p-tabs`: it owns the active `value` and coordinates the projected
 * `<tum-ui-tab-list>` / `<tum-ui-tab-panels>` through a DI-provided {@link TumUiTabsService}. The `value`
 * is a two-way `model`, so both `[(value)]` and the one-way `[value]` + `(valueChange)` form used across
 * the admin screens work. Values may be numbers or strings (parity with `p-tabs`).
 */
@Component({
    selector: 'tum-ui-tabs',
    template: '<ng-content />',
    host: {
        class: 'tum-ui-tabs flex flex-col',
    },
    providers: [TumUiTabsService],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiTabsComponent {
    private readonly tabsService = inject(TumUiTabsService);

    /** Active tab value. Two-way bindable via `[(value)]`, or one-way `[value]` + `(valueChange)`. */
    readonly value = model<number | string>();

    constructor() {
        // Feed the container's model into the shared service: it drives every descendant's active state, and a
        // tab click routes back through `select` -> `value.set`, which emits `valueChange` for one-way callers.
        this.tabsService.register(this.value, (selected) => this.value.set(selected));
    }
}
