import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, afterRenderEffect, computed, contentChildren, effect, inject, signal, untracked } from '@angular/core';
import { TabList } from '@angular/aria/tabs';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronLeft, faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiTabComponent } from './tumaet-ui-tab.component';
import { TumAetUiTabsService, tabKey, tabValue } from './tumaet-ui-tabs.service';

/**
 * Scrollable tab list with an animated selection indicator. Tabs that do not fit scroll, and a button at either cut-off
 * end scrolls the list on by most of its width.
 *
 * An Angular Aria tab list: it owns the `tablist` role and the keyboard model. The arrow keys move between tabs, following
 * the text direction and wrapping at either end, Home and End jump to the first and last tab, and focusing a tab selects
 * it. The list keeps the selection on an enabled tab: when the bound value matches no tab, or its tab is disabled or
 * removed, it selects the first enabled tab instead.
 */
@Component({
    selector: 'tumaet-ui-tab-list',
    templateUrl: './tumaet-ui-tab-list.component.html',
    styleUrl: './tumaet-ui-tab-list.component.scss',
    imports: [FaIconComponent],
    hostDirectives: [TabList],
    host: {
        class: 'tumaet-ui-tab-list tumaet:relative tumaet:flex tumaet:w-full tumaet:min-w-0 tumaet:max-w-full tumaet:overflow-x-auto tumaet:border-b tumaet:border-border',
        '(focusin)': 'revealFocusedTab($event)',
        '(scroll)': 'updateScrollButtons()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiTabListComponent implements OnDestroy {
    private readonly tabsService = inject(TumAetUiTabsService);
    private readonly tabList = inject(TabList);
    private readonly elementRef = inject<ElementRef<HTMLElement>>(ElementRef);
    /**
     * The rendered tabs, in the order they are shown, for placing the indicator. Only their element and selection are
     * read: the query reports a tab declared inside `@if` or `@for` before its `value` binding has been applied.
     */
    private readonly renderedTabs = contentChildren(TumAetUiTabComponent, { descendants: true });
    private resizeObserver?: ResizeObserver;
    protected readonly indicatorPosition = signal({ offset: 0, width: 0, animate: false });
    protected readonly indicatorTransform = computed(() => `translateX(${this.indicatorPosition().offset}px)`);
    private indicatorReady = false;
    /** Whether tabs are cut off before the visible part of the list, which a button at its start then scrolls to. */
    protected readonly canScrollStart = signal(false);
    /** Whether tabs are cut off after the visible part of the list, which a button at its end then scrolls to. */
    protected readonly canScrollEnd = signal(false);
    private readonly rightToLeft = signal(false);
    protected readonly scrollStartIcon = computed(() => (this.rightToLeft() ? faChevronRight : faChevronLeft));
    protected readonly scrollEndIcon = computed(() => (this.rightToLeft() ? faChevronLeft : faChevronRight));

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
            // The scroll buttons follow the layout observer, which also reports once as it starts: a signal written here,
            // after rendering, would not show the buttons until something else rendered the list again
            this.observeLayout(tabs);
        });
    }

    ngOnDestroy(): void {
        this.resizeObserver?.disconnect();
    }

    /**
     * Scrolls a focused tab fully into the list. Aria moves focus with `focus()`, which leaves a tab that is already
     * partly visible where it is, so in a narrow, scrolling list the tab the keyboard reached could stay cut off.
     */
    protected revealFocusedTab(event: FocusEvent): void {
        const tab = this.renderedTabs().find((candidate) => candidate.element === event.target);
        tab?.element.scrollIntoView?.({ block: 'nearest', inline: 'nearest' });
    }

    /** Scrolls the list towards its start or end by most of its width, keeping a little of the previous view for context. */
    protected scrollTabs(towards: 'start' | 'end'): void {
        const list = this.elementRef.nativeElement;
        // scrollLeft runs from 0 towards negative values in right-to-left layouts, so the end lies to the left there
        const towardsLeft = (towards === 'start') !== this.rightToLeft();
        const step = list.clientWidth * 0.75;
        list.scrollBy?.({ left: towardsLeft ? -step : step, behavior: 'smooth' });
    }

    protected updateScrollButtons(): void {
        const list = this.elementRef.nativeElement;
        this.rightToLeft.set(getComputedStyle(list).direction === 'rtl');
        const scrolled = Math.abs(list.scrollLeft);
        const overflow = list.scrollWidth - list.clientWidth;
        // A pixel of tolerance, as zoomed layouts report fractional widths that never quite reach the end
        this.canScrollStart.set(scrolled > 1);
        this.canScrollEnd.set(overflow - scrolled > 1);
    }

    private updateIndicator(active: TumAetUiTabComponent | undefined): void {
        const width = active?.element.offsetWidth ?? 0;
        this.indicatorPosition.set({
            offset: active?.element.offsetLeft ?? 0,
            width,
            animate: this.indicatorReady,
        });
        this.indicatorReady ||= width > 0;
    }

    private observeLayout(tabs: readonly TumAetUiTabComponent[]): void {
        if (typeof ResizeObserver === 'undefined') {
            return;
        }
        this.resizeObserver?.disconnect();
        this.resizeObserver = new ResizeObserver(() => {
            this.updateIndicator(this.renderedTabs().find((tab) => tab.selected()));
            this.updateScrollButtons();
        });
        this.resizeObserver.observe(this.elementRef.nativeElement);
        tabs.forEach((tab) => this.resizeObserver!.observe(tab.element));
    }
}
