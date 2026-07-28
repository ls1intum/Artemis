import { ChangeDetectionStrategy, Component, ElementRef, afterRenderEffect, contentChildren, inject, viewChild } from '@angular/core';
import { TumUiTabsService } from './tum-ui-tabs.service';
import { TumUiTabComponent } from './tum-ui-tab.component';

@Component({
    selector: 'tum-ui-tab-list',
    template: `
        <ng-content />
        <div #activeBar class="tum-ui-tab-list-active-bar tum:bg-tum-ui-primary" aria-hidden="true"></div>
    `,
    styleUrl: './tum-ui-tab-list.component.scss',
    host: {
        role: 'tablist',
        class: 'tum-ui-tab-list tum:relative tum:flex tum:border-b tum:border-tum-ui-border',
        '(keydown)': 'onKeydown($event)',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiTabListComponent {
    private readonly tabsService = inject(TumUiTabsService);
    private readonly tabs = contentChildren(TumUiTabComponent, { descendants: true });
    private readonly activeBar = viewChild.required<ElementRef<HTMLElement>>('activeBar');

    constructor() {
        afterRenderEffect(() => {
            const active = this.tabsService.active();
            const activeTab = this.tabs().find((tab) => tab.value() === active);
            const element = activeTab?.elementRef.nativeElement;
            const bar = this.activeBar().nativeElement;
            if (element && element.offsetWidth > 0) {
                bar.style.width = `${element.offsetWidth}px`;
                bar.style.transform = `translateX(${element.offsetLeft}px)`;
                bar.style.opacity = '1';
            } else {
                bar.style.opacity = '0';
            }
        });
    }

    protected onKeydown(event: KeyboardEvent): void {
        const enabledTabs = this.tabs().filter((tab) => !tab.disabled());
        if (enabledTabs.length === 0) {
            return;
        }
        const currentIndex = enabledTabs.findIndex((tab) => tab.elementRef.nativeElement === event.target);
        let nextIndex: number | undefined;
        switch (event.key) {
            case 'ArrowRight':
            case 'ArrowDown':
                nextIndex = (currentIndex + 1) % enabledTabs.length;
                break;
            case 'ArrowLeft':
            case 'ArrowUp':
                nextIndex = (currentIndex - 1 + enabledTabs.length) % enabledTabs.length;
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
