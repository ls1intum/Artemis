import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Component, ErrorHandler, signal } from '@angular/core';
import { Directionality } from '@angular/cdk/bidi';
import { END, HOME, LEFT_ARROW, RIGHT_ARROW } from '@angular/cdk/keycodes';
import { vi } from 'vitest';
import { TumUiTabsComponent } from './tum-ui-tabs.component';
import { TumUiTabListComponent } from './tum-ui-tab-list.component';
import { TumUiTabComponent } from './tum-ui-tab.component';
import { TumUiTabPanelsComponent } from './tum-ui-tab-panels.component';
import { TumUiTabPanelComponent } from './tum-ui-tab-panel.component';

@Component({
    template: `
        <tum-ui-tabs [value]="value()" (valueChange)="onValueChange($event)">
            <tum-ui-tab-list>
                <tum-ui-tab [value]="1">One</tum-ui-tab>
                <tum-ui-tab [value]="2">Two</tum-ui-tab>
                <tum-ui-tab [value]="3" [disabled]="thirdDisabled()">Three</tum-ui-tab>
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
    readonly value = signal<number | string>(1);
    readonly thirdDisabled = signal(false);
    changes: (number | string | undefined)[] = [];

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
    let directionality: { value: 'ltr' | 'rtl' };

    beforeEach(async () => {
        directionality = { value: 'ltr' };
        await TestBed.configureTestingModule({
            imports: [TabsHostComponent],
            providers: [{ provide: Directionality, useValue: directionality }],
        }).compileComponents();
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

    function press(tab: HTMLElement, key: string, keyCode: number): void {
        tab.dispatchEvent(new KeyboardEvent('keydown', { key, keyCode, bubbles: true }));
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
        expect(tabs()[3].getAttribute('tabindex')).toBe('-1');
    });

    it('moves focus and activates with ArrowRight / ArrowLeft (wrapping, skipping disabled)', () => {
        press(tabs()[0], 'ArrowRight', RIGHT_ARROW);
        fixture.detectChanges();
        expect(host.value()).toBe(2);
        expect(document.activeElement).toBe(tabs()[1]);

        press(tabs()[0], 'ArrowLeft', LEFT_ARROW);
        fixture.detectChanges();
        expect(host.value()).toBe(3);
        expect(document.activeElement).toBe(tabs()[2]);
    });

    it('jumps to first / last enabled tab with Home / End', () => {
        press(tabs()[0], 'End', END);
        fixture.detectChanges();
        expect(host.value()).toBe(3);

        press(tabs()[2], 'Home', HOME);
        fixture.detectChanges();
        expect(host.value()).toBe(1);
    });

    it('reverses horizontal arrow navigation in right-to-left layouts', () => {
        directionality.value = 'rtl';
        press(tabs()[0], 'ArrowRight', RIGHT_ARROW);
        fixture.detectChanges();
        expect(host.value()).toBe(3);
        expect(document.activeElement).toBe(tabs()[2]);
    });

    it('reflects an externally changed value (one-way [value] binding)', () => {
        host.value.set(3);
        fixture.detectChanges();
        expect(tabs()[2].getAttribute('aria-selected')).toBe('true');
        expect(element.textContent).toContain('Panel Three');
    });

    it('selects the first enabled tab when the active value is missing', () => {
        host.value.set('missing');
        fixture.detectChanges();
        expect(host.value()).toBe(1);
        expect(tabs()[0].getAttribute('tabindex')).toBe('0');
    });

    it('reacts when the active tab becomes disabled', () => {
        host.value.set(3);
        fixture.detectChanges();
        host.thirdDisabled.set(true);
        fixture.detectChanges();
        expect(host.value()).toBe(1);
        expect(tabs()[2].getAttribute('aria-disabled')).toBe('true');
        expect(tabs()[2].getAttribute('tabindex')).toBe('-1');
    });
});

@Component({
    template: `
        <tum-ui-tabs [(value)]="value">
            <tum-ui-tab-list>
                <tum-ui-tab value="course settings">Course settings</tum-ui-tab>
                <tum-ui-tab [value]="1">Number one</tum-ui-tab>
                <tum-ui-tab value="1">String one</tum-ui-tab>
            </tum-ui-tab-list>
            <tum-ui-tab-panels>
                <tum-ui-tab-panel value="course settings">Course settings panel</tum-ui-tab-panel>
                <tum-ui-tab-panel [value]="1">Number one panel</tum-ui-tab-panel>
                <tum-ui-tab-panel value="1">String one panel</tum-ui-tab-panel>
            </tum-ui-tab-panels>
        </tum-ui-tabs>
    `,
    imports: [TumUiTabsComponent, TumUiTabListComponent, TumUiTabComponent, TumUiTabPanelsComponent, TumUiTabPanelComponent],
})
class StringTabsHostComponent {
    value: number | string = 'course settings';
}

describe('TumUiTabs family (string values)', () => {
    it('creates valid, distinct ARIA relationships for string and numeric values', () => {
        const fixture = TestBed.createComponent(StringTabsHostComponent);
        fixture.detectChanges();
        const element = fixture.nativeElement as HTMLElement;
        const tabs = fixture.debugElement.queryAll(By.css('tum-ui-tab')).map((debug) => debug.nativeElement as HTMLElement);
        const panels = fixture.debugElement.queryAll(By.css('tum-ui-tab-panel')).map((debug) => debug.nativeElement as HTMLElement);

        expect(tabs.map((tab) => tab.id)).toHaveLength(new Set(tabs.map((tab) => tab.id)).size);
        expect(panels.map((panel) => panel.id)).toHaveLength(new Set(panels.map((panel) => panel.id)).size);
        expect([...tabs, ...panels].every((item) => !/\s/.test(item.id))).toBe(true);
        tabs.forEach((tab, index) => {
            expect(tab.getAttribute('aria-controls')).toBe(panels[index].id);
            expect(panels[index].getAttribute('aria-labelledby')).toBe(tab.id);
        });

        tabs[1].click();
        fixture.detectChanges();
        expect(fixture.componentInstance.value).toBe(1);
        tabs[2].click();
        fixture.detectChanges();
        expect(fixture.componentInstance.value).toBe('1');
        expect(element.textContent).toContain('String one');
    });
});

@Component({
    template: `
        <tum-ui-tabs [value]="value()" (valueChange)="onValueChange($event)">
            <tum-ui-tab-list>
                @for (tab of tabs(); track tab.value) {
                    <tum-ui-tab [value]="tab.value">{{ tab.label }}</tum-ui-tab>
                }
                @if (showExtra()) {
                    <tum-ui-tab value="extra">Extra</tum-ui-tab>
                }
            </tum-ui-tab-list>
            <tum-ui-tab-panels>
                @for (tab of tabs(); track tab.value) {
                    <tum-ui-tab-panel [value]="tab.value">Panel {{ tab.label }}</tum-ui-tab-panel>
                }
            </tum-ui-tab-panels>
        </tum-ui-tabs>
    `,
    imports: [TumUiTabsComponent, TumUiTabListComponent, TumUiTabComponent, TumUiTabPanelsComponent, TumUiTabPanelComponent],
})
class ControlFlowTabsHostComponent {
    readonly tabs = signal([
        { value: 'general', label: 'General' },
        { value: 'security', label: 'Security' },
        { value: 'application', label: 'Application' },
    ]);
    readonly showExtra = signal(false);
    readonly value = signal<number | string>('general');

    onValueChange(next: number | string | undefined): void {
        if (next !== undefined) {
            this.value.set(next);
        }
    }
}

/**
 * Tabs generated by control flow. A tab's `value` is a required input, and the tab list's content query reports such a
 * tab before Angular has applied the binding, so a list that read the input directly threw NG0950 on every render and
 * left the keyboard manager unsynchronised.
 */
describe('TumUiTabs family (tabs declared with @for / @if)', () => {
    let fixture: ComponentFixture<ControlFlowTabsHostComponent>;
    let host: ControlFlowTabsHostComponent;
    let errors: unknown[];

    beforeEach(async () => {
        errors = [];
        await TestBed.configureTestingModule({
            imports: [ControlFlowTabsHostComponent],
            providers: [
                { provide: Directionality, useValue: { value: 'ltr' } },
                { provide: ErrorHandler, useValue: { handleError: (error: unknown) => errors.push(error) } },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(ControlFlowTabsHostComponent);
        host = fixture.componentInstance;
        fixture.detectChanges();
    });

    function tabs(): HTMLElement[] {
        return fixture.debugElement.queryAll(By.css('tum-ui-tab')).map((debug) => debug.nativeElement);
    }

    it('renders without reporting an error, and does not overwrite the bound value', () => {
        expect(errors).toEqual([]);
        expect(host.value()).toBe('general');
        expect(tabs()).toHaveLength(3);
        expect(tabs()[0].getAttribute('aria-selected')).toBe('true');
    });

    it('activates a generated tab on click', () => {
        tabs()[2].click();
        fixture.detectChanges();

        expect(host.value()).toBe('application');
        expect(tabs()[2].getAttribute('aria-selected')).toBe('true');
        expect(fixture.nativeElement.textContent).toContain('Panel Application');
        expect(errors).toEqual([]);
    });

    it('navigates generated tabs with the keyboard', () => {
        tabs()[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', keyCode: RIGHT_ARROW, bubbles: true }));
        fixture.detectChanges();

        expect(host.value()).toBe('security');
        expect(document.activeElement).toBe(tabs()[1]);
    });

    it('picks up a tab added by @if after the first render', () => {
        host.showExtra.set(true);
        fixture.detectChanges();

        expect(tabs()).toHaveLength(4);
        expect(errors).toEqual([]);

        tabs()[3].click();
        fixture.detectChanges();
        expect(host.value()).toBe('extra');
    });

    it('falls back to the first remaining tab when the active one is removed', () => {
        host.value.set('application');
        fixture.detectChanges();
        expect(tabs()[2].getAttribute('aria-selected')).toBe('true');

        host.tabs.update((tabList) => tabList.filter((tab) => tab.value !== 'application'));
        fixture.detectChanges();

        expect(tabs()).toHaveLength(2);
        expect(host.value()).toBe('general');
        expect(errors).toEqual([]);
    });

    it('reorders with the list, so keyboard order follows what is rendered', () => {
        host.tabs.update((tabList) => [...tabList].reverse());
        fixture.detectChanges();

        expect(tabs().map((tab) => tab.textContent?.trim())).toEqual(['Application', 'Security', 'General']);
        expect(host.value()).toBe('general');
        expect(tabs()[2].getAttribute('aria-selected')).toBe('true');
        expect(errors).toEqual([]);
    });
});

@Component({
    template: `
        <tum-ui-tabs [value]="value()" (valueChange)="value.set($event ?? 1)">
            <tum-ui-tab-list>
                <tum-ui-tab [value]="1">Files</tum-ui-tab>
                <tum-ui-tab [value]="2">Statement</tum-ui-tab>
            </tum-ui-tab-list>
            <tum-ui-tab-panels>
                <tum-ui-tab-panel [value]="1" preserveContent><span class="kept">Files</span></tum-ui-tab-panel>
                <tum-ui-tab-panel [value]="2"><span class="discarded">Statement</span></tum-ui-tab-panel>
            </tum-ui-tab-panels>
        </tum-ui-tabs>
    `,
    imports: [TumUiTabsComponent, TumUiTabListComponent, TumUiTabComponent, TumUiTabPanelsComponent, TumUiTabPanelComponent],
})
class PreserveContentHostComponent {
    readonly value = signal<number | string>(1);
}

describe('TumUiTabPanelComponent preserveContent', () => {
    let fixture: ComponentFixture<PreserveContentHostComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [PreserveContentHostComponent] }).compileComponents();
        fixture = TestBed.createComponent(PreserveContentHostComponent);
        fixture.detectChanges();
    });

    function panel(value: number): HTMLElement {
        return fixture.debugElement.queryAll(By.css('tum-ui-tab-panel'))[value - 1].nativeElement as HTMLElement;
    }

    it('destroys an inactive panel by default', () => {
        expect(fixture.debugElement.query(By.css('.discarded'))).toBeNull();
        fixture.componentInstance.value.set(2);
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('.discarded'))).not.toBeNull();
        fixture.componentInstance.value.set(1);
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('.discarded'))).toBeNull();
    });

    it('keeps a preserved panel in the DOM, so a trip to another tab does not reset its state', () => {
        fixture.componentInstance.value.set(2);
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('.kept'))).not.toBeNull();
    });

    it('takes the preserved panel out of the accessibility tree and out of the tab order while hidden', () => {
        fixture.componentInstance.value.set(2);
        fixture.detectChanges();
        const preserved = panel(1);
        expect(preserved.hasAttribute('hidden')).toBe(true);
        expect(preserved.hasAttribute('inert')).toBe(true);
        expect(preserved.getAttribute('tabindex')).toBeNull();
        expect(preserved.getAttribute('data-state')).toBe('inactive');
        expect(panel(2).getAttribute('data-state')).toBe('active');
        expect(panel(2).hasAttribute('inert')).toBe(false);
    });
});
