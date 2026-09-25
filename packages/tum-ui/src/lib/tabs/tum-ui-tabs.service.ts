import { Injectable, Signal, computed, signal } from '@angular/core';

export type TumUiTabValue = number | string | undefined;

let nextGroupId = 0;

@Injectable()
export class TumUiTabsService {
    private readonly groupId = `tum-ui-tabs-${nextGroupId++}`;

    private readonly source = signal<Signal<TumUiTabValue>>(signal<TumUiTabValue>(undefined));

    /**
     * Content queries can expose a tab before its required input is bound. Tabs publish from their own
     * effects; a missing entry tells the list to wait before selecting a fallback.
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

    /** Read inside `update` to avoid subscribing each tab's effect to every other tab's value. */
    publish(tab: object, value: number | string): void {
        this.publishedValues.update((values) => (values.get(tab) === value ? values : new Map(values).set(tab, value)));
    }

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
