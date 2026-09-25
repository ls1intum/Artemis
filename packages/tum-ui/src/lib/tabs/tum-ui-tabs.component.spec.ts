import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Component, ErrorHandler, signal } from '@angular/core';
import { Directionality } from '@angular/cdk/bidi';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { TabsHarness } from '@angular/aria/tabs/testing';
import { vi } from 'vitest';
import { TumUiTabsComponent } from './tum-ui-tabs.component';
import { TumUiTabListComponent } from './tum-ui-tab-list.component';
import { TumUiTabComponent } from './tum-ui-tab.component';
import { TumUiTabPanelsComponent } from './tum-ui-tab-panels.component';
import { TumUiTabPanelComponent } from './tum-ui-tab-panel.component';

const TABS_IMPORTS = [TumUiTabsComponent, TumUiTabListComponent, TumUiTabComponent, TumUiTabPanelsComponent, TumUiTabPanelComponent];

/** Aria reports a broken tab structure, a tab without a panel for example, as a development-mode console warning. */
function ariaViolations(warn: ReturnType<typeof vi.spyOn>): unknown[][] {
    return warn.mock.calls.filter((call: unknown[]) => call.some((argument) => typeof argument === 'string' && /violations|ngTab/i.test(argument)));
}

@Component({
    template: `
        <tum-ui-tabs [value]="value()" (valueChange)="onValueChange($event)">
            <tum-ui-tab-list aria-label="Numbers">
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
    imports: TABS_IMPORTS,
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
    let warn: ReturnType<typeof vi.spyOn>;

    beforeEach(async () => {
        warn = vi.spyOn(console, 'warn');
        await TestBed.configureTestingModule({ imports: [TabsHostComponent] }).compileComponents();
        fixture = TestBed.createComponent(TabsHostComponent);
        host = fixture.componentInstance;
        element = fixture.nativeElement as HTMLElement;
        fixture.detectChanges();
        await fixture.whenStable();
    });

    afterEach(() => vi.restoreAllMocks());

    function tabs(): HTMLElement[] {
        return fixture.debugElement.queryAll(By.css('tum-ui-tab')).map((debug) => debug.nativeElement);
    }

    function panels(): HTMLElement[] {
        return fixture.debugElement.queryAll(By.css('tum-ui-tab-panel [role="tabpanel"]')).map((debug) => debug.nativeElement);
    }

    async function press(target: HTMLElement, key: string): Promise<void> {
        target.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
        fixture.detectChanges();
        await fixture.whenStable();
    }

    async function click(target: HTMLElement): Promise<void> {
        target.click();
        fixture.detectChanges();
        await fixture.whenStable();
    }

    it('renders the ARIA tabs structure (tablist / tab / tabpanel roles)', () => {
        const tabList = fixture.debugElement.query(By.css('tum-ui-tab-list')).nativeElement as HTMLElement;
        expect(tabList.getAttribute('role')).toBe('tablist');
        expect(tabList.getAttribute('aria-orientation')).toBe('horizontal');
        expect(tabList.getAttribute('aria-label')).toBe('Numbers');
        expect(tabs()).toHaveLength(4);
        tabs().forEach((tab) => expect(tab.getAttribute('role')).toBe('tab'));
        expect(panels()).toHaveLength(4);
        expect(ariaViolations(warn)).toEqual([]);
    });

    it('shows only the active panel and marks the active tab', () => {
        expect(element.textContent).toContain('Panel One');
        expect(element.textContent).not.toContain('Panel Two');
        expect(tabs()[0].getAttribute('aria-selected')).toBe('true');
        expect(tabs()[1].getAttribute('aria-selected')).toBe('false');
        expect(panels()[0].hidden).toBe(false);
        expect(panels()[1].hidden).toBe(true);
        expect(panels()[1].hasAttribute('inert')).toBe(true);
    });

    it('wires aria-controls / aria-labelledby between a tab and its panel', () => {
        tabs().forEach((tab, index) => {
            expect(tab.getAttribute('aria-controls')).toBe(panels()[index].id);
            expect(panels()[index].getAttribute('aria-labelledby')).toBe(tab.id);
        });
    });

    it('uses a roving tabindex (only the active tab is tabbable)', () => {
        expect(tabs().map((tab) => tab.getAttribute('tabindex'))).toEqual(['0', '-1', '-1', '-1']);
        expect(panels()[0].getAttribute('tabindex')).toBe('0');
    });

    it('activates a tab on click and emits valueChange', async () => {
        await click(tabs()[1]);

        expect(host.value()).toBe(2);
        expect(host.changes).toContain(2);
        expect(element.textContent).toContain('Panel Two');
        expect(element.textContent).not.toContain('Panel One');
        expect(tabs()[1].getAttribute('aria-selected')).toBe('true');
        expect(tabs().map((tab) => tab.getAttribute('tabindex'))).toEqual(['-1', '0', '-1', '-1']);
    });

    it('does not select a disabled tab, but announces it as disabled', async () => {
        await click(tabs()[3]);

        expect(host.value()).toBe(1);
        expect(host.changes).not.toContain(4);
        expect(tabs()[3].getAttribute('aria-disabled')).toBe('true');
        expect(tabs()[3].getAttribute('aria-selected')).toBe('false');
        expect(tabs()[3].classList).toContain('tum-ui-tab-disabled');
    });

    it('selects the focused tab with ArrowRight / ArrowLeft, wrapping at either end', async () => {
        await press(tabs()[0], 'ArrowRight');
        expect(host.value()).toBe(2);
        expect(document.activeElement).toBe(tabs()[1]);

        await press(tabs()[1], 'ArrowLeft');
        expect(host.value()).toBe(1);
        expect(document.activeElement).toBe(tabs()[0]);

        // Wrapping lands on the disabled last tab: it takes focus so it can be discovered, and the selection stays.
        await press(tabs()[0], 'ArrowLeft');
        expect(document.activeElement).toBe(tabs()[3]);
        expect(host.value()).toBe(1);

        await press(tabs()[3], 'ArrowLeft');
        expect(document.activeElement).toBe(tabs()[2]);
        expect(host.value()).toBe(3);
    });

    it('moves to the first / last tab with Home / End', async () => {
        await press(tabs()[0], 'End');
        expect(document.activeElement).toBe(tabs()[3]);
        expect(host.value()).toBe(1);

        await press(tabs()[3], 'Home');
        expect(document.activeElement).toBe(tabs()[0]);
        expect(host.value()).toBe(1);
    });

    it('scrolls the tab the keyboard reaches fully into a narrow list', async () => {
        const scrollIntoView = vi.fn();
        tabs()[1].scrollIntoView = scrollIntoView;

        await press(tabs()[0], 'ArrowRight');

        expect(document.activeElement).toBe(tabs()[1]);
        expect(scrollIntoView).toHaveBeenCalledWith({ block: 'nearest', inline: 'nearest' });
    });

    it('reverses horizontal arrow navigation in right-to-left layouts', async () => {
        TestBed.inject(Directionality).valueSignal.set('rtl');

        await press(tabs()[0], 'ArrowLeft');
        expect(host.value()).toBe(2);
        expect(document.activeElement).toBe(tabs()[1]);

        await press(tabs()[1], 'ArrowRight');
        expect(host.value()).toBe(1);
        expect(document.activeElement).toBe(tabs()[0]);
    });

    it('reflects an externally changed value (one-way [value] binding)', async () => {
        host.value.set(3);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(tabs()[2].getAttribute('aria-selected')).toBe('true');
        expect(element.textContent).toContain('Panel Three');
        expect(element.textContent).not.toContain('Panel One');
    });

    it('selects the first enabled tab when the active value is missing', async () => {
        host.value.set('missing');
        fixture.detectChanges();
        await fixture.whenStable();

        expect(host.value()).toBe(1);
        expect(tabs()[0].getAttribute('aria-selected')).toBe('true');
        expect(tabs()[0].getAttribute('tabindex')).toBe('0');
    });

    it('selects the first enabled tab when the active tab becomes disabled', async () => {
        host.value.set(3);
        fixture.detectChanges();
        await fixture.whenStable();
        host.thirdDisabled.set(true);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(host.value()).toBe(1);
        expect(tabs()[0].getAttribute('aria-selected')).toBe('true');
        expect(tabs()[2].getAttribute('aria-disabled')).toBe('true');
    });

    it('is driven by the Angular Aria tabs harness', async () => {
        const harness = await TestbedHarnessEnvironment.loader(fixture).getHarness(TabsHarness);

        expect(await (await harness.getSelectedTab())?.getTitle()).toBe('One');
        expect(await harness.getTabs({ disabled: true })).toHaveLength(1);

        await harness.selectTab({ title: 'Three' });
        expect(host.value()).toBe(3);
        expect(await (await harness.getSelectedTab())?.getTitle()).toBe('Three');
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
    imports: TABS_IMPORTS,
})
class StringTabsHostComponent {
    value: number | string = 'course settings';
}

describe('TumUiTabs family (string values)', () => {
    it('creates valid, distinct ARIA relationships for string and numeric values', async () => {
        const fixture = TestBed.createComponent(StringTabsHostComponent);
        fixture.detectChanges();
        await fixture.whenStable();
        const element = fixture.nativeElement as HTMLElement;
        const tabs = fixture.debugElement.queryAll(By.css('tum-ui-tab')).map((debug) => debug.nativeElement as HTMLElement);
        const panels = fixture.debugElement.queryAll(By.css('tum-ui-tab-panel [role="tabpanel"]')).map((debug) => debug.nativeElement as HTMLElement);

        expect(tabs.map((tab) => tab.id)).toHaveLength(new Set(tabs.map((tab) => tab.id)).size);
        expect(panels.map((panel) => panel.id)).toHaveLength(new Set(panels.map((panel) => panel.id)).size);
        expect([...tabs, ...panels].every((item) => item.id && !/\s/.test(item.id))).toBe(true);
        tabs.forEach((tab, index) => {
            expect(tab.getAttribute('aria-controls')).toBe(panels[index].id);
            expect(panels[index].getAttribute('aria-labelledby')).toBe(tab.id);
        });

        tabs[1].click();
        fixture.detectChanges();
        await fixture.whenStable();
        expect(fixture.componentInstance.value).toBe(1);
        expect(element.textContent).toContain('Number one panel');

        tabs[2].click();
        fixture.detectChanges();
        await fixture.whenStable();
        expect(fixture.componentInstance.value).toBe('1');
        expect(element.textContent).toContain('String one panel');
        expect(element.textContent).not.toContain('Number one panel');
    });
});

@Component({
    template: `
        <tum-ui-tabs [value]="value()" (valueChange)="value.set($event ?? 1)">
            <tum-ui-tab-list>
                <tum-ui-tab [value]="1">One</tum-ui-tab>
                @if (showTwo()) {
                    <tum-ui-tab [value]="2">Two</tum-ui-tab>
                }
            </tum-ui-tab-list>
            <tum-ui-tab-panels>
                <tum-ui-tab-panel [value]="1">Panel One</tum-ui-tab-panel>
                <tum-ui-tab-panel [value]="2">Panel Two</tum-ui-tab-panel>
                <tum-ui-tab-panel value="1">Panel String One</tum-ui-tab-panel>
            </tum-ui-tab-panels>
        </tum-ui-tabs>
    `,
    imports: TABS_IMPORTS,
})
class PanelWithoutTabHostComponent {
    readonly value = signal<number | string>(1);
    readonly showTwo = signal(false);
}

/** A panel whose value matches no tab, because its tab is inside a false `@if` or the types differ, stays hidden. */
describe('TumUiTabs family (panels without a tab)', () => {
    it('shows only the panel of the active value', async () => {
        const fixture = TestBed.createComponent(PanelWithoutTabHostComponent);
        fixture.detectChanges();
        await fixture.whenStable();
        const panels = () => fixture.debugElement.queryAll(By.css('tum-ui-tab-panel [role="tabpanel"]')).map((debug) => debug.nativeElement as HTMLElement);
        const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

        expect(panels().map((panel) => panel.hidden)).toEqual([false, true, true]);
        expect(text()).toContain('Panel One');
        expect(text()).not.toContain('Panel Two');
        expect(text()).not.toContain('Panel String One');

        fixture.componentInstance.showTwo.set(true);
        fixture.detectChanges();
        await fixture.whenStable();
        (fixture.debugElement.queryAll(By.css('tum-ui-tab'))[1].nativeElement as HTMLElement).click();
        fixture.detectChanges();
        await fixture.whenStable();

        expect(fixture.componentInstance.value()).toBe(2);
        expect(panels().map((panel) => panel.hidden)).toEqual([true, false, true]);
        expect(text()).toContain('Panel Two');
        expect(text()).not.toContain('Panel One');
        expect(text()).not.toContain('Panel String One');
    });
});

@Component({
    template: `
        <tum-ui-tabs [value]="value()" (valueChange)="value.set($event ?? 'general')">
            <tum-ui-tab-list>
                <tum-ui-tab value="general">General</tum-ui-tab>
                <tum-ui-tab value="security">Security</tum-ui-tab>
            </tum-ui-tab-list>
        </tum-ui-tabs>
        <p>Showing {{ value() }}</p>
    `,
    imports: [TumUiTabsComponent, TumUiTabListComponent, TumUiTabComponent],
})
class PanellessTabsHostComponent {
    readonly value = signal<number | string>('general');
}

/** A tab list without panels switches a view the host renders itself, as several Artemis admin pages do. */
describe('TumUiTabs family (tabs without panels)', () => {
    it('switches the bound value without reporting a broken tab structure', async () => {
        const warn = vi.spyOn(console, 'warn');
        const fixture = TestBed.createComponent(PanellessTabsHostComponent);
        fixture.detectChanges();
        await fixture.whenStable();
        const tabs = fixture.debugElement.queryAll(By.css('tum-ui-tab')).map((debug) => debug.nativeElement as HTMLElement);

        tabs[1].click();
        fixture.detectChanges();
        await fixture.whenStable();

        expect(fixture.componentInstance.value()).toBe('security');
        expect(tabs[1].getAttribute('aria-selected')).toBe('true');
        expect(fixture.nativeElement.textContent).toContain('Showing security');
        expect(ariaViolations(warn)).toEqual([]);
        // Each tab controls an empty panel that stays hidden, so nothing but the host's own view is shown.
        const stubPanels = [...fixture.nativeElement.querySelectorAll('[role="tabpanel"]')] as HTMLElement[];
        expect(stubPanels).toHaveLength(2);
        expect(stubPanels.every((panel) => panel.hidden && panel.childElementCount === 0)).toBe(true);
        vi.restoreAllMocks();
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
    imports: TABS_IMPORTS,
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
 * Tabs generated by control flow. A tab's `value` is a required input, and a content query reports such a tab before
 * Angular has applied the binding, so reading it from the list threw NG0950 on every render.
 */
describe('TumUiTabs family (tabs declared with @for / @if)', () => {
    let fixture: ComponentFixture<ControlFlowTabsHostComponent>;
    let host: ControlFlowTabsHostComponent;
    let errors: unknown[];

    beforeEach(async () => {
        errors = [];
        await TestBed.configureTestingModule({
            imports: [ControlFlowTabsHostComponent],
            providers: [{ provide: ErrorHandler, useValue: { handleError: (error: unknown) => errors.push(error) } }],
        }).compileComponents();
        fixture = TestBed.createComponent(ControlFlowTabsHostComponent);
        host = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();
    });

    function tabs(): HTMLElement[] {
        return fixture.debugElement.queryAll(By.css('tum-ui-tab')).map((debug) => debug.nativeElement);
    }

    async function settle(): Promise<void> {
        fixture.detectChanges();
        await fixture.whenStable();
    }

    it('renders without reporting an error, and does not overwrite the bound value', () => {
        expect(errors).toEqual([]);
        expect(host.value()).toBe('general');
        expect(tabs()).toHaveLength(3);
        expect(tabs()[0].getAttribute('aria-selected')).toBe('true');
    });

    it('activates a generated tab on click', async () => {
        tabs()[2].click();
        await settle();

        expect(host.value()).toBe('application');
        expect(tabs()[2].getAttribute('aria-selected')).toBe('true');
        expect(fixture.nativeElement.textContent).toContain('Panel Application');
        expect(errors).toEqual([]);
    });

    it('navigates generated tabs with the keyboard', async () => {
        tabs()[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true, cancelable: true }));
        await settle();

        expect(host.value()).toBe('security');
        expect(document.activeElement).toBe(tabs()[1]);
    });

    it('picks up a tab added by @if after the first render', async () => {
        host.showExtra.set(true);
        await settle();

        expect(tabs()).toHaveLength(4);
        expect(errors).toEqual([]);

        tabs()[3].click();
        await settle();
        expect(host.value()).toBe('extra');
    });

    it('falls back to the first remaining tab when the active one is removed', async () => {
        host.value.set('application');
        await settle();
        expect(tabs()[2].getAttribute('aria-selected')).toBe('true');

        host.tabs.update((tabList) => tabList.filter((tab) => tab.value !== 'application'));
        await settle();

        expect(tabs()).toHaveLength(2);
        expect(host.value()).toBe('general');
        expect(tabs()[0].getAttribute('aria-selected')).toBe('true');
        expect(errors).toEqual([]);
    });

    it('reorders with the list, so keyboard order follows what is rendered', async () => {
        host.tabs.update((tabList) => [...tabList].reverse());
        await settle();

        expect(tabs().map((tab) => tab.textContent?.trim())).toEqual(['Application', 'Security', 'General']);
        expect(host.value()).toBe('general');
        expect(tabs()[2].getAttribute('aria-selected')).toBe('true');

        tabs()[2].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft', bubbles: true, cancelable: true }));
        await settle();
        expect(host.value()).toBe('security');
        expect(errors).toEqual([]);
    });
});
