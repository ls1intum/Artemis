import { Injectable, Signal, WritableSignal, computed, signal } from '@angular/core';

export type TumAetUiTabValue = number | string | undefined;

/**
 * Angular Aria identifies a tab and its panel by one string. TUM AET UI accepts numbers and strings and keeps `1` apart from
 * `'1'`, so each value is prefixed with its type. The key never leaves the package: `valueChange` reports the value.
 */
export function tabKey(value: number | string): string {
    return `${typeof value === 'number' ? 'number' : 'string'}:${value}`;
}

/** The value a {@link tabKey} was made from. */
export function tabValue(key: string): number | string {
    const separator = key.indexOf(':');
    const value = key.slice(separator + 1);
    return key.slice(0, separator) === 'number' ? Number(value) : value;
}

/** A panel as its tabs container tracks it. */
export interface TumAetUiTabsPanelEntry {
    readonly key: Signal<string>;
}

/** A tab as its tabs container tracks it. */
export interface TumAetUiTabsTabEntry extends TumAetUiTabsPanelEntry {
    readonly element: HTMLElement;
    readonly disabled: Signal<boolean>;
    readonly selected: Signal<boolean>;
}

/** Selection state shared by one `tumaet-ui-tabs` and the tabs and panels inside it. */
@Injectable()
export class TumAetUiTabsService {
    private readonly source = signal<Signal<TumAetUiTabValue>>(signal<TumAetUiTabValue>(undefined));
    private onSelect: (value: TumAetUiTabValue) => void = () => {};

    private readonly tabSet = signal<ReadonlySet<TumAetUiTabsTabEntry>>(new Set());
    private readonly panelSet = signal<ReadonlySet<TumAetUiTabsPanelEntry>>(new Set());

    /** The value of the selected tab, as bound on `tumaet-ui-tabs`. */
    readonly active = computed<TumAetUiTabValue>(() => this.source()());

    /**
     * The tabs in document order, which is also the order the keyboard moves through them. Sorted on every call, because
     * `@for` reorders tabs by moving their elements rather than by recreating them.
     */
    orderedTabs(): TumAetUiTabsTabEntry[] {
        return [...this.tabSet()].sort((first, second) => (first.element.compareDocumentPosition(second.element) & Node.DOCUMENT_POSITION_FOLLOWING ? -1 : 1));
    }

    /**
     * Keys of the tabs that have no panel. A tab list without panels is a supported way to switch a view the host renders
     * itself; the container gives each such tab an empty, hidden placeholder panel, which only keeps aria from reporting
     * a missing panel in development mode.
     */
    readonly keysWithoutPanel = computed(() => {
        const panelKeys = new Set([...this.panelSet()].map((panel) => panel.key()));
        return [...this.tabSet()].map((tab) => tab.key()).filter((key) => !panelKeys.has(key));
    });

    register(value: Signal<TumAetUiTabValue>, onSelect: (value: TumAetUiTabValue) => void): void {
        this.source.set(value);
        this.onSelect = onSelect;
    }

    select(value: TumAetUiTabValue): void {
        this.onSelect(value);
    }

    /**
     * Tracks a tab from its `ngOnInit`, once Angular has applied its bindings: a content query reports a tab declared
     * inside `@if` or `@for` before that, and reading its required `value` then throws NG0950.
     *
     * @returns the function that stops tracking it
     */
    addTab(tab: TumAetUiTabsTabEntry): () => void {
        return this.track(this.tabSet, tab);
    }

    /** Tracks a panel from its `ngOnInit`, for the same reason as {@link addTab}. */
    addPanel(panel: TumAetUiTabsPanelEntry): () => void {
        return this.track(this.panelSet, panel);
    }

    private track<T>(members: WritableSignal<ReadonlySet<T>>, member: T): () => void {
        members.update((current) => new Set(current).add(member));
        return () =>
            members.update((current) => {
                const remaining = new Set(current);
                remaining.delete(member);
                return remaining;
            });
    }
}
