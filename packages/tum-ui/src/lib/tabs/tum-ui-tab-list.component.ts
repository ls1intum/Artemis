import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, afterRenderEffect, computed, contentChildren, effect, inject, signal, untracked } from '@angular/core';
import { TabList } from '@angular/aria/tabs';
import { TumUiTabComponent } from './tum-ui-tab.component';
import { TumUiTabsService, tabKey, tabValue } from './tum-ui-tabs.service';

/**
 * Scrollable tab list with an animated selection indicator.
 *
 * An Angular Aria tab list: it owns the `tablist` role and the keyboard model. The arrow keys move between tabs, following
 * the text direction and wrapping at either end, Home and End jump to the first and last tab, and focusing a tab selects
 * it. The list keeps the selection on an enabled tab: when the bound value matches no tab, or its tab is disabled or
 * removed, it selects the first enabled tab instead.
 */
@Component({
    selector: 'tum-ui-tab-list',
    templateUrl: './tum-ui-tab-list.component.html',
    styleUrl: './tum-ui-tab-list.component.scss',
    hostDirectives: [TabList],
    host: {
        class: 'tum-ui-tab-list tum:relative tum:flex tum:w-full tum:min-w-0 tum:max-w-full tum:overflow-x-auto tum:border-b tum:border-border',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiTabListComponent implements OnDestroy {
    private readonly tabsService = inject(TumUiTabsService);
    private readonly tabList = inject(TabList);
    private readonly elementRef = inject<ElementRef<HTMLElement>>(ElementRef);
    /**
     * The rendered tabs, in the order they are shown, for placing the indicator. Only their element and selection are
     * read: the query reports a tab declared inside `@if` or `@for` before its `value` binding has been applied.
     */
    private readonly renderedTabs = contentChildren(TumUiTabComponent, { descendants: true });
    private resizeObserver?: ResizeObserver;
    protected readonly indicatorPosition = signal({ offset: 0, width: 0, animate: false });
    protected readonly indicatorTransform = computed(() => `translateX(${this.indicatorPosition().offset}px)`);
    private indicatorReady = false;

    constructor() {
        // The bound value drives aria's selection.
        effect(() => {
            const active = this.tabsService.active();
            const key = active === undefined ? undefined : tabKey(active);
            untracked(() => {
                if (this.tabList.selectedTab() !== key) {
                    this.tabList.selectedTab.set(key);
                }
            });
        });
        // Aria's selection, changed by a click or the keyboard, flows back into the bound value, and a selection that
        // lands on no enabled tab falls back to the first enabled one.
        effect(() => {
            const tabs = this.tabsService.orderedTabs();
            const selected = tabs.find((tab) => tab.selected() && !tab.disabled()) ?? tabs.find((tab) => !tab.disabled());
            if (selected) {
                const value = tabValue(selected.key());
                untracked(() => {
                    if (this.tabsService.active() !== value) {
                        this.tabsService.select(value);
                    }
                });
            }
        });
        afterRenderEffect(() => {
            const tabs = this.renderedTabs();
            this.updateIndicator(tabs.find((tab) => tab.selected()));
            this.observeLayout(tabs);
        });
    }

    ngOnDestroy(): void {
        this.resizeObserver?.disconnect();
    }

    private updateIndicator(active: TumUiTabComponent | undefined): void {
        const width = active?.element.offsetWidth ?? 0;
        this.indicatorPosition.set({
            offset: active?.element.offsetLeft ?? 0,
            width,
            animate: this.indicatorReady,
        });
        this.indicatorReady ||= width > 0;
    }

    private observeLayout(tabs: readonly TumUiTabComponent[]): void {
        if (typeof ResizeObserver === 'undefined') {
            return;
        }
        this.resizeObserver?.disconnect();
        this.resizeObserver = new ResizeObserver(() => this.updateIndicator(this.renderedTabs().find((tab) => tab.selected())));
        this.resizeObserver.observe(this.elementRef.nativeElement);
        tabs.forEach((tab) => this.resizeObserver!.observe(tab.element));
    }
}
