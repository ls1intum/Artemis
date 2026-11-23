import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, signal } from '@angular/core';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { AccordionGroupHarness, AccordionHarness } from '@angular/aria/accordion/testing';
import { TumAetUiAccordionComponent } from './tumaet-ui-accordion.component';
import { TumAetUiAccordionPanelComponent } from './tumaet-ui-accordion-panel.component';

@Component({
    template: `
        <tumaet-ui-accordion [multiExpandable]="multiple()">
            <tumaet-ui-accordion-panel header="Languages" [(expanded)]="firstExpanded">Java and Python</tumaet-ui-accordion-panel>
            <tumaet-ui-accordion-panel [headingLevel]="4">
                <span tumAetUiAccordionHeader>Grading</span>
                <a href="#grading">Automatic</a>
            </tumaet-ui-accordion-panel>
            <tumaet-ui-accordion-panel header="Archive" [disabled]="true">Hidden</tumaet-ui-accordion-panel>
        </tumaet-ui-accordion>
    `,
    imports: [TumAetUiAccordionComponent, TumAetUiAccordionPanelComponent],
})
class AccordionHostComponent {
    readonly multiple = signal(false);
    readonly firstExpanded = signal(false);
}

@Component({
    template: `
        <tumaet-ui-accordion>
            <tumaet-ui-accordion-panel header="First">One</tumaet-ui-accordion-panel>
            <tumaet-ui-accordion-panel header="Second">Two</tumaet-ui-accordion-panel>
        </tumaet-ui-accordion>
    `,
    imports: [TumAetUiAccordionComponent, TumAetUiAccordionPanelComponent],
})
class UnboundAccordionHostComponent {}

describe('TumAetUiAccordion without bindings', () => {
    it('opens one panel at a time', async () => {
        const fixture = TestBed.createComponent(UnboundAccordionHostComponent);
        await fixture.whenStable();
        const headers = () => Array.from((fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('button'));

        headers()[0].click();
        await fixture.whenStable();
        headers()[1].click();
        await fixture.whenStable();

        expect(headers().map((header) => header.getAttribute('aria-expanded'))).toEqual(['false', 'true']);
    });
});

describe('TumAetUiAccordion', () => {
    let fixture: ComponentFixture<AccordionHostComponent>;
    let host: AccordionHostComponent;
    let element: HTMLElement;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [AccordionHostComponent] }).compileComponents();
        fixture = TestBed.createComponent(AccordionHostComponent);
        host = fixture.componentInstance;
        element = fixture.nativeElement as HTMLElement;
        fixture.detectChanges();
        await fixture.whenStable();
    });

    function headers(): HTMLButtonElement[] {
        return Array.from(element.querySelectorAll<HTMLButtonElement>('button'));
    }

    function regions(): HTMLElement[] {
        return Array.from(element.querySelectorAll<HTMLElement>('[role="region"]'));
    }

    async function settle(): Promise<void> {
        fixture.detectChanges();
        await fixture.whenStable();
    }

    async function press(target: HTMLElement, key: string): Promise<void> {
        target.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
        await settle();
    }

    it('renders a labelled header button and region per panel, all collapsed and inert', () => {
        expect(headers().map((header) => header.textContent?.trim())).toEqual(['Languages', 'Grading', 'Archive']);
        headers().forEach((header, index) => {
            expect(header.getAttribute('aria-expanded')).toBe('false');
            expect(header.getAttribute('aria-controls')).toBe(regions()[index].id);
            expect(regions()[index].getAttribute('aria-labelledby')).toBe(header.id);
            expect(regions()[index].hasAttribute('inert')).toBe(true);
        });
    });

    it('wraps each header in a heading of the requested level', () => {
        const levels = Array.from(element.querySelectorAll('[role="heading"]')).map((heading) => heading.getAttribute('aria-level'));
        expect(levels).toEqual(['3', '4', '3']);
    });

    it('expands a panel on click, reports it through the two-way binding and collapses it again', async () => {
        headers()[0].click();
        await settle();

        expect(host.firstExpanded()).toBe(true);
        expect(headers()[0].getAttribute('aria-expanded')).toBe('true');
        expect(regions()[0].hasAttribute('inert')).toBe(false);

        headers()[0].click();
        await settle();

        expect(host.firstExpanded()).toBe(false);
        expect(regions()[0].hasAttribute('inert')).toBe(true);
    });

    it('follows the bound expansion state', async () => {
        host.firstExpanded.set(true);
        await settle();

        expect(headers()[0].getAttribute('aria-expanded')).toBe('true');
    });

    it('keeps one panel open at a time by default', async () => {
        headers()[0].click();
        await settle();
        headers()[1].click();
        await settle();

        expect(headers().map((header) => header.getAttribute('aria-expanded'))).toEqual(['false', 'true', 'false']);
        expect(host.firstExpanded()).toBe(false);
    });

    it('keeps several panels open when multiExpandable is set', async () => {
        host.multiple.set(true);
        await settle();
        headers()[0].click();
        await settle();
        headers()[1].click();
        await settle();

        expect(headers().map((header) => header.getAttribute('aria-expanded'))).toEqual(['true', 'true', 'false']);
    });

    it('closes all but the first open panel when multiExpandable is switched off', async () => {
        host.multiple.set(true);
        await settle();
        headers()[0].click();
        await settle();
        headers()[1].click();
        await settle();
        expect(headers().map((header) => header.getAttribute('aria-expanded'))).toEqual(['true', 'true', 'false']);

        host.multiple.set(false);
        await settle();

        expect(headers().map((header) => header.getAttribute('aria-expanded'))).toEqual(['true', 'false', 'false']);
    });

    it('moves focus between the headers with the arrow keys, Home and End, and toggles with Enter', async () => {
        headers()[0].focus();
        await press(headers()[0], 'ArrowDown');
        expect(document.activeElement).toBe(headers()[1]);

        await press(headers()[1], 'Enter');
        expect(headers()[1].getAttribute('aria-expanded')).toBe('true');

        await press(headers()[1], 'End');
        expect(document.activeElement).toBe(headers()[2]);

        await press(headers()[2], 'Home');
        expect(document.activeElement).toBe(headers()[0]);
    });

    it('announces a disabled panel as unavailable and does not expand it', async () => {
        expect(headers()[2].getAttribute('aria-disabled')).toBe('true');

        headers()[2].click();
        await settle();

        expect(headers()[2].getAttribute('aria-expanded')).toBe('false');
    });

    it('can be driven with the Angular Aria harnesses', async () => {
        const loader = TestbedHarnessEnvironment.loader(fixture);
        await loader.getHarness(AccordionGroupHarness);
        const grading = await loader.getHarness(AccordionHarness.with({ title: 'Grading' }));

        expect(await grading.isExpanded()).toBe(false);
        await grading.expand();

        expect(await grading.isExpanded()).toBe(true);
        expect(element.querySelector('a')?.closest('[inert]')).toBeNull();
    });
});
