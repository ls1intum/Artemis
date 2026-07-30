import { FocusKeyManager } from '@angular/cdk/a11y';
import { Directionality } from '@angular/cdk/bidi';
import { ChangeDetectionStrategy, Component, Injector, OnDestroy, contentChildren, effect, inject } from '@angular/core';
import { Subscription } from 'rxjs';
import { TumUiTabsService } from './tum-ui-tabs.service';
import { TumUiTabComponent } from './tum-ui-tab.component';

@Component({
    selector: 'tum-ui-tab-list',
    template: '<ng-content />',
    host: {
        role: 'tablist',
        class: 'tum-ui-tab-list tum:relative tum:flex tum:border-b tum:border-border',
        '(keydown)': 'onKeydown($event)',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiTabListComponent implements OnDestroy {
    private readonly tabsService = inject(TumUiTabsService);
    private readonly directionality = inject(Directionality);
    private readonly injector = inject(Injector);
    private readonly tabs = contentChildren(TumUiTabComponent, { descendants: true });
    private readonly keyManager = new FocusKeyManager(this.tabs, this.injector).withWrap().withHomeAndEnd().setFocusOrigin('keyboard');
    private readonly keyManagerChange: Subscription;

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
        this.keyManagerChange.unsubscribe();
        this.keyManager.destroy();
    }
}
