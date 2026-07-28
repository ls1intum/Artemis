import { Injectable, Signal, computed, signal } from '@angular/core';

/**
 * A tab / panel key. Numbers and strings are both supported so the family is a drop-in for PrimeNG's
 * `p-tabs` (whose `value` is `string | number`). `undefined` is the "no active tab" state before a
 * value is bound.
 */
export type TumUiTabValue = number | string | undefined;

let nextGroupId = 0;

/**
 * Shared coordination state for a single tum-ui-tabs group, provided by `<tum-ui-tabs>` and injected by
 * its `<tum-ui-tab>` / `<tum-ui-tab-panel>` descendants via DI. It exposes the active value reactively
 * (mirrored from the container's `value` model) and generates the matching `id` / `aria-controls` /
 * `aria-labelledby` links between a tab and its panel.
 */
@Injectable()
export class TumUiTabsService {
    private readonly groupId = `tum-ui-tabs-${nextGroupId++}`;

    private readonly source = signal<Signal<TumUiTabValue>>(signal<TumUiTabValue>(undefined));
    private onSelect: (value: TumUiTabValue) => void = () => {};

    /** The currently active tab value, tracked reactively from the container's `value` model. */
    readonly active = computed<TumUiTabValue>(() => this.source()());

    /**
     * Wires the service to the container's model. Called once by `<tum-ui-tabs>` in its constructor.
     * @param value the container's `value` signal (its model), read reactively as the active value
     * @param onSelect writes a new value back to the model (which emits `valueChange`)
     */
    register(value: Signal<TumUiTabValue>, onSelect: (value: TumUiTabValue) => void): void {
        this.source.set(value);
        this.onSelect = onSelect;
    }

    /** Activates the given value, writing it back through the container's model so `valueChange` fires. */
    select(value: TumUiTabValue): void {
        this.onSelect(value);
    }

    /** Stable DOM id for the tab that owns `value` (used as the panel's `aria-labelledby`). */
    tabId(value: TumUiTabValue): string {
        return `${this.groupId}-tab-${value}`;
    }

    /** Stable DOM id for the panel that owns `value` (used as the tab's `aria-controls`). */
    panelId(value: TumUiTabValue): string {
        return `${this.groupId}-panel-${value}`;
    }
}
