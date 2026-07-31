import { ChangeDetectionStrategy, Component, ElementRef, afterRenderEffect, contentChildren, inject, viewChild } from '@angular/core';
import { TumUiTabsService } from 'app/shared-ui/tum-ui/tabs/tum-ui-tabs.service';
import { TumUiTabComponent } from 'app/shared-ui/tum-ui/tabs/tum-ui-tab.component';

/**
 * The `role="tablist"` bar, part of the tum-aet-ui kit. Drop-in replacement for PrimeNG's `p-tablist`.
 *
 * Projects the `<tum-ui-tab>` children, draws the Aura active-bar (a primary underline that slides to the
 * active tab), and owns the roving-tabindex keyboard navigation (Arrow/Home/End with automatic activation,
 * skipping disabled tabs), matching the WAI-ARIA tabs pattern and PrimeNG's behavior.
 */
@Component({
    selector: 'tum-ui-tab-list',
    template: `
        <ng-content />
        <div #activeBar class="tum-ui-tab-list-active-bar bg-primary" aria-hidden="true"></div>
    `,
    styleUrl: './tum-ui-tab-list.component.scss',
    host: {
        role: 'tablist',
        class: 'tum-ui-tab-list relative flex border-b',
        '(keydown)': 'onKeydown($event)',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiTabListComponent {
    private readonly tabsService = inject(TumUiTabsService);
    private readonly tabs = contentChildren(TumUiTabComponent, { descendants: true });
    private readonly activeBar = viewChild.required<ElementRef<HTMLElement>>('activeBar');

    constructor() {
        // Slide the active bar under the active tab whenever the active value or the set of tabs changes.
        // Measurement is skipped gracefully when the element has no layout (e.g. headless tests / detached DOM):
        // the active tab's own primary bottom border still renders the indicator, so nothing looks broken.
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
        // Automatic activation (matches PrimeNG + the WAI-ARIA tabs pattern): moving focus also selects.
        nextTab.elementRef.nativeElement.focus();
        this.tabsService.select(nextTab.value());
    }
}
