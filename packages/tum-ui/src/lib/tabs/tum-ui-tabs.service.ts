import { Injectable, Signal, computed, signal } from '@angular/core';

export type TumUiTabValue = number | string | undefined;

let nextGroupId = 0;

@Injectable()
export class TumUiTabsService {
    private readonly groupId = `tum-ui-tabs-${nextGroupId++}`;

    private readonly source = signal<Signal<TumUiTabValue>>(signal<TumUiTabValue>(undefined));
    private onSelect: (value: TumUiTabValue) => void = () => {};
    readonly active = computed<TumUiTabValue>(() => this.source()());
    register(value: Signal<TumUiTabValue>, onSelect: (value: TumUiTabValue) => void): void {
        this.source.set(value);
        this.onSelect = onSelect;
    }
    select(value: TumUiTabValue): void {
        this.onSelect(value);
    }
    tabId(value: TumUiTabValue): string {
        return `${this.groupId}-tab-${value}`;
    }
    panelId(value: TumUiTabValue): string {
        return `${this.groupId}-panel-${value}`;
    }
}
