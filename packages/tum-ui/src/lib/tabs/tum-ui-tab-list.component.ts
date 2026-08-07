import { FocusKeyManager } from '@angular/cdk/a11y';
import { Directionality } from '@angular/cdk/bidi';
import { ChangeDetectionStrategy, Component, ElementRef, Injector, OnDestroy, afterRenderEffect, computed, contentChildren, effect, inject, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { TumUiTabsService } from './tum-ui-tabs.service';
import { TumUiTabComponent } from './tum-ui-tab.component';

/** Scrollable tab-list container with keyboard navigation and an animated selection indicator. */
@Component({
    selector: 'tum-ui-tab-list',
    templateUrl: './tum-ui-tab-list.component.html',
    styleUrl: './tum-ui-tab-list.component.scss',
    host: {
        role: 'tablist',
        class: 'tum-ui-tab-list tum:relative tum:flex tum:w-full tum:min-w-0 tum:max-w-full tum:overflow-x-auto tum:border-b tum:border-border',
        '(keydown)': 'onKeydown($event)',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiTabListComponent implements OnDestroy {
    private readonly tabsService = inject(TumUiTabsService);
    private readonly directionality = inject(Directionality);
    private readonly injector = inject(Injector);
    private readonly elementRef = inject<ElementRef<HTMLElement>>(ElementRef);
    private readonly tabs = contentChildren(TumUiTabComponent, { descendants: true });
    private readonly keyManager = new FocusKeyManager(this.tabs, this.injector).withWrap().withHomeAndEnd().setFocusOrigin('keyboard');
    private readonly keyManagerChange: Subscription;
    private resizeObserver?: ResizeObserver;
    protected readonly indicatorPosition = signal({ offset: 0, width: 0, animate: false });
    protected readonly indicatorTransform = computed(() => `translateX(${this.indicatorPosition().offset}px)`);
    private indicatorReady = false;

    constructor() {
        this.keyManagerChange = this.keyManager.change.subscribe((index) => {
            const tab = this.tabs()[index];
            if (tab) {
                this.tabsService.select(tab.value());
            }
        });
        effect(() => {
            const tabs = this.tabs();
            const activeValue = this.tabsService.active();
            const activeIndex = tabs.findIndex((tab) => tab.value() === activeValue && !tab.disabled);
            if (activeIndex >= 0) {
                this.keyManager.updateActiveItem(activeIndex);
                return;
            }
            const firstEnabledIndex = tabs.findIndex((tab) => !tab.disabled);
            if (firstEnabledIndex >= 0) {
                this.keyManager.updateActiveItem(firstEnabledIndex);
                this.tabsService.select(tabs[firstEnabledIndex].value());
            }
        });
        afterRenderEffect(() => {
            const tabs = this.tabs();
            const active = tabs.find((tab) => tab.value() === this.tabsService.active());
            this.updateIndicator(active);
            this.observeLayout(tabs);
        });
    }

    protected onKeydown(event: KeyboardEvent): void {
        if (event.target instanceof HTMLElement) {
            const eventIndex = this.tabs().findIndex((tab) => tab.elementRef.nativeElement === event.target);
            if (eventIndex >= 0) {
                this.keyManager.updateActiveItem(eventIndex);
            }
        }
        this.keyManager.withHorizontalOrientation(this.directionality.value).onKeydown(event);
    }

    ngOnDestroy(): void {
        this.resizeObserver?.disconnect();
        this.keyManagerChange.unsubscribe();
        this.keyManager.destroy();
    }

    private updateIndicator(active: TumUiTabComponent | undefined): void {
        const width = active?.elementRef.nativeElement.offsetWidth ?? 0;
        this.indicatorPosition.set({
            offset: active?.elementRef.nativeElement.offsetLeft ?? 0,
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
        this.resizeObserver = new ResizeObserver(() => {
            const active = this.tabs().find((tab) => tab.value() === this.tabsService.active());
            this.updateIndicator(active);
        });
        this.resizeObserver.observe(this.elementRef.nativeElement);
        tabs.forEach((tab) => this.resizeObserver!.observe(tab.elementRef.nativeElement));
    }
}
