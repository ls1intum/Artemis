import { Injectable, Signal, computed, signal } from '@angular/core';

export type TumUiTabValue = number | string | undefined;

let nextGroupId = 0;

@Injectable()
export class TumUiTabsService {
    private readonly groupId = `tum-ui-tabs-${nextGroupId++}`;

    private readonly source = signal<Signal<TumUiTabValue>>(signal<TumUiTabValue>(undefined));

    /**
     * The value each tab has published, keyed by the tab instance.
     *
     * A tab's `value` is a required input, and the tab list's content query reports a tab declared inside `@if` or
     * `@for` before Angular has applied that binding — reading the input from the list would then throw NG0950. Each
     * tab instead publishes its value from its own change detection, where the input is always available, and the list
     * reads it back from here. A tab missing from this map therefore means "not bound yet", which the list waits for
     * rather than acting on.
     */
    private readonly publishedValues = signal<ReadonlyMap<object, number | string>>(new Map());

    private onSelect: (value: TumUiTabValue) => void = () => {};
    readonly active = computed<TumUiTabValue>(() => this.source()());
    register(value: Signal<TumUiTabValue>, onSelect: (value: TumUiTabValue) => void): void {
        this.source.set(value);
        this.onSelect = onSelect;
    }
    select(value: TumUiTabValue): void {
        this.onSelect(value);
    }

    /**
     * Publishes a tab's value, or replaces it when the tab's input changes.
     *
     * The unchanged case returns the same map instance, so the signal does not notify and the tab list is not woken for
     * nothing. The check lives inside `update` on purpose: each tab calls this from its own effect, and reading the
     * signal here instead would subscribe every tab's effect to every other tab's value.
     */
    publish(tab: object, value: number | string): void {
        this.publishedValues.update((values) => (values.get(tab) === value ? values : new Map(values).set(tab, value)));
    }

    /** Withdraws a destroyed tab's value, so a removed tab cannot keep the list waiting for or matching it. */
    unpublish(tab: object): void {
        this.publishedValues.update((values) => {
            if (!values.has(tab)) {
                return values;
            }
            const remaining = new Map(values);
            remaining.delete(tab);
            return remaining;
        });
    }

    /** The tab's published value, or `undefined` while its `value` input has not been applied yet. */
    valueFor(tab: object): TumUiTabValue {
        return this.publishedValues().get(tab);
    }

    tabId(value: TumUiTabValue): string {
        return `${this.groupId}-tab-${this.idSegment(value)}`;
    }
    panelId(value: TumUiTabValue): string {
        return `${this.groupId}-panel-${this.idSegment(value)}`;
    }
    private idSegment(value: TumUiTabValue): string {
        const type = typeof value === 'number' ? 'number' : typeof value === 'string' ? 'string' : 'undefined';
        return `${type}-${encodeURIComponent(String(value))}`;
    }
}
