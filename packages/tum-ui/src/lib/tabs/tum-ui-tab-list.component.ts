import { ChangeDetectionStrategy, Component, contentChildren, inject } from '@angular/core';
import { Directionality } from '@angular/cdk/bidi';
import { TumUiTabsService } from './tum-ui-tabs.service';
import { TumUiTabComponent } from './tum-ui-tab.component';

@Component({
    selector: 'tum-ui-tab-list',
    template: '<ng-content />',
    host: {
        role: 'tablist',
        class: 'tum-ui-tab-list tum:relative tum:flex tum:border-b tum:border-tum-ui-border',
        '(keydown)': 'onKeydown($event)',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiTabListComponent {
    private readonly tabsService = inject(TumUiTabsService);
    private readonly directionality = inject(Directionality);
    private readonly tabs = contentChildren(TumUiTabComponent, { descendants: true });

    protected onKeydown(event: KeyboardEvent): void {
        const enabledTabs = this.tabs().filter((tab) => !tab.disabled());
        if (enabledTabs.length === 0) {
            return;
        }
        const currentIndex = enabledTabs.findIndex((tab) => tab.elementRef.nativeElement === event.target);
        let nextIndex: number | undefined;
        switch (event.key) {
            case 'ArrowRight':
                nextIndex = (currentIndex + (this.directionality.value === 'rtl' ? -1 : 1) + enabledTabs.length) % enabledTabs.length;
                break;
            case 'ArrowLeft':
                nextIndex = (currentIndex + (this.directionality.value === 'rtl' ? 1 : -1) + enabledTabs.length) % enabledTabs.length;
                break;
            case 'Home':
                nextIndex = 0;
                break;
            case 'End':
                nextIndex = enabledTabs.length - 1;
                break;
            default:
                return;
        }
        event.preventDefault();
        const nextTab = enabledTabs[nextIndex];
        nextTab.elementRef.nativeElement.focus();
        this.tabsService.select(nextTab.value());
    }
}
