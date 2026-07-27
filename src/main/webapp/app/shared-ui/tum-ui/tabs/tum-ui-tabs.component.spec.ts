import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Component, signal } from '@angular/core';
import { vi } from 'vitest';
import { TumUiTabsComponent } from 'app/shared-ui/tum-ui/tabs/tum-ui-tabs.component';
import { TumUiTabListComponent } from 'app/shared-ui/tum-ui/tabs/tum-ui-tab-list.component';
import { TumUiTabComponent } from 'app/shared-ui/tum-ui/tabs/tum-ui-tab.component';
import { TumUiTabPanelsComponent } from 'app/shared-ui/tum-ui/tabs/tum-ui-tab-panels.component';
import { TumUiTabPanelComponent } from 'app/shared-ui/tum-ui/tabs/tum-ui-tab-panel.component';

@Component({
    template: `
        <tum-ui-tabs [value]="value()" (valueChange)="onValueChange($event)">
            <tum-ui-tab-list>
                <tum-ui-tab [value]="1">One</tum-ui-tab>
                <tum-ui-tab [value]="2">Two</tum-ui-tab>
                <tum-ui-tab [value]="3">Three</tum-ui-tab>
                <tum-ui-tab [value]="4" [disabled]="true">Four</tum-ui-tab>
            </tum-ui-tab-list>
            <tum-ui-tab-panels>
                <tum-ui-tab-panel [value]="1">Panel One</tum-ui-tab-panel>
                <tum-ui-tab-panel [value]="2">Panel Two</tum-ui-tab-panel>
                <tum-ui-tab-panel [value]="3">Panel Three</tum-ui-tab-panel>
                <tum-ui-tab-panel [value]="4">Panel Four</tum-ui-tab-panel>
            </tum-ui-tab-panels>
        </tum-ui-tabs>
    `,
    imports: [TumUiTabsComponent, TumUiTabListComponent, TumUiTabComponent, TumUiTabPanelsComponent, TumUiTabPanelComponent],
})
class TabsHostComponent {
    // A signal source, exactly like the admin usage (`[value]="activeTab()"`).
    readonly value = signal<number | string>(1);
    changes: (number | string | undefined)[] = [];

    // Mirrors the admin usage: a one-way [value] with a (valueChange) handler that updates the source signal.
    onValueChange(next: number | string | undefined): void {
        this.changes.push(next);
        if (next !== undefined) {
            this.value.set(next);
        }
    }
}

describe('TumUiTabs family', () => {
    let fixture: ComponentFixture<TabsHostComponent>;
    let host: TabsHostComponent;
    let element: HTMLElement;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [TabsHostComponent] }).compileComponents();
        fixture = TestBed.createComponent(TabsHostComponent);
        host = fixture.componentInstance;
        element = fixture.nativeElement as HTMLElement;
        fixture.detectChanges();
    });

    afterEach(() => vi.restoreAllMocks());

    function tabs(): HTMLElement[] {
        return fixture.debugElement.queryAll(By.css('tum-ui-tab')).map((debug) => debug.nativeElement);
    }

    function panels(): HTMLElement[] {
        return fixture.debugElement.queryAll(By.css('tum-ui-tab-panel')).map((debug) => debug.nativeElement);
    }

    it('renders the ARIA tabs structure (tablist / tab / tabpanel roles)', () => {
        expect(fixture.debugElement.query(By.css('[role="tablist"]'))).not.toBeNull();
        expect(tabs()).toHaveLength(4);
        tabs().forEach((tab) => expect(tab.getAttribute('role')).toBe('tab'));
        panels().forEach((panel) => expect(panel.getAttribute('role')).toBe('tabpanel'));
    });

    it('shows only the active panel and marks the active tab', () => {
        expect(element.textContent).toContain('Panel One');
        expect(element.textContent).not.toContain('Panel Two');
        expect(tabs()[0].getAttribute('aria-selected')).toBe('true');
        expect(tabs()[1].getAttribute('aria-selected')).toBe('false');
        // Inactive panels are hidden for assistive tech.
        expect(panels()[1].hasAttribute('hidden')).toBe(true);
        expect(panels()[0].hasAttribute('hidden')).toBe(false);
    });

    it('wires aria-controls / aria-labelledby between a tab and its panel', () => {
        const tabId = tabs()[0].getAttribute('id');
        const panelId = panels()[0].getAttribute('id');
        expect(tabs()[0].getAttribute('aria-controls')).toBe(panelId);
        expect(panels()[0].getAttribute('aria-labelledby')).toBe(tabId);
    });

    it('uses a roving tabindex (only the active tab is tabbable)', () => {
        expect(tabs()[0].getAttribute('tabindex')).toBe('0');
        expect(tabs()[1].getAttribute('tabindex')).toBe('-1');
    });

    it('activates a tab on click and emits valueChange', () => {
        tabs()[1].click();
        fixture.detectChanges();
        expect(host.value()).toBe(2);
        expect(host.changes).toContain(2);
        expect(element.textContent).toContain('Panel Two');
        expect(element.textContent).not.toContain('Panel One');
        expect(tabs()[1].getAttribute('aria-selected')).toBe('true');
    });

    it('does not activate a disabled tab', () => {
        tabs()[3].click();
        fixture.detectChanges();
        expect(host.value()).toBe(1);
        expect(tabs()[3].getAttribute('aria-disabled')).toBe('true');
    });

    it('moves focus and activates with ArrowRight / ArrowLeft (wrapping, skipping disabled)', () => {
        tabs()[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
        fixture.detectChanges();
        expect(host.value()).toBe(2);
        expect(document.activeElement).toBe(tabs()[1]);

        // ArrowLeft from the first enabled tab wraps to the last ENABLED tab (index 2), skipping the disabled one.
        tabs()[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft', bubbles: true }));
        fixture.detectChanges();
        expect(host.value()).toBe(3);
        expect(document.activeElement).toBe(tabs()[2]);
    });

    it('jumps to first / last enabled tab with Home / End', () => {
        tabs()[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'End', bubbles: true }));
        fixture.detectChanges();
        expect(host.value()).toBe(3);

        tabs()[2].dispatchEvent(new KeyboardEvent('keydown', { key: 'Home', bubbles: true }));
        fixture.detectChanges();
        expect(host.value()).toBe(1);
    });

    it('reflects an externally changed value (one-way [value] binding)', () => {
        host.value.set(3);
        fixture.detectChanges();
        expect(tabs()[2].getAttribute('aria-selected')).toBe('true');
        expect(element.textContent).toContain('Panel Three');
    });
});

@Component({
    template: `
        <tum-ui-tabs [(value)]="value">
            <tum-ui-tab-list>
                <tum-ui-tab [value]="'a'">A</tum-ui-tab>
                <tum-ui-tab [value]="'b'">B</tum-ui-tab>
            </tum-ui-tab-list>
            <tum-ui-tab-panels>
                <tum-ui-tab-panel [value]="'a'">Panel A</tum-ui-tab-panel>
                <tum-ui-tab-panel [value]="'b'">Panel B</tum-ui-tab-panel>
            </tum-ui-tab-panels>
        </tum-ui-tabs>
    `,
    imports: [TumUiTabsComponent, TumUiTabListComponent, TumUiTabComponent, TumUiTabPanelsComponent, TumUiTabPanelComponent],
})
class StringTabsHostComponent {
    value = 'a';
}

describe('TumUiTabs family (string values)', () => {
    it('supports string tab keys', () => {
        const fixture = TestBed.createComponent(StringTabsHostComponent);
        fixture.detectChanges();
        const element = fixture.nativeElement as HTMLElement;
        expect(element.textContent).toContain('Panel A');
        expect(element.textContent).not.toContain('Panel B');

        const secondTab = fixture.debugElement.queryAll(By.css('tum-ui-tab'))[1].nativeElement as HTMLElement;
        secondTab.click();
        fixture.detectChanges();
        expect(fixture.componentInstance.value).toBe('b');
        expect(element.textContent).toContain('Panel B');
    });
});
