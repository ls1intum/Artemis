import { Component, input } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { Splitter } from 'primeng/splitter';
import { FeedbackSplitComponent } from 'app/exercise/feedback-bottom-panel/feedback-split.component';

const STORAGE_KEY = 'feedback-split-spec';

@Component({
    imports: [FeedbackSplitComponent],
    template: `
        <ng-template #panel><div data-testid="bottom">Panel</div></ng-template>
        <jhi-feedback-split
            [storageKey]="storageKey"
            [hasBottom]="hasBottom()"
            [bottomTemplate]="panel"
            [expanded]="expanded()"
            (collapseRequested)="collapseRequests = collapseRequests + 1"
            [collapseSnapPercent]="snapPercent()"
        >
            <div feedbackSplitTop data-testid="top">Content</div>
        </jhi-feedback-split>
    `,
})
class HostComponent {
    readonly storageKey = STORAGE_KEY;
    readonly hasBottom = input(true);
    readonly expanded = input(true);
    readonly snapPercent = input(15);
    collapseRequests = 0;
}

describe('FeedbackSplitComponent', () => {
    let fixture: ComponentFixture<HostComponent>;

    const create = (inputs: { hasBottom?: boolean; expanded?: boolean } = {}) => {
        fixture = TestBed.createComponent(HostComponent);
        Object.entries(inputs).forEach(([name, value]) => fixture.componentRef.setInput(name, value));
        fixture.detectChanges();
    };
    const splitter = () => fixture.debugElement.query(By.directive(Splitter));
    const split = (): HTMLElement | null => fixture.nativeElement.querySelector('[data-testid="feedback-split"]');
    const top = (): HTMLElement | null => fixture.nativeElement.querySelector('[data-testid="top"]');
    const bottom = (): HTMLElement | null => fixture.nativeElement.querySelector('[data-testid="bottom"]');

    beforeEach(async () => {
        localStorage.removeItem(STORAGE_KEY);
        await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
    });

    afterEach(() => localStorage.removeItem(STORAGE_KEY));

    it('should divide the content from the panel with a vertical splitter', () => {
        create();

        expect(split()).not.toBeNull();
        expect(splitter().componentInstance.layout).toBe('vertical');
        expect(top()).not.toBeNull();
        expect(bottom()).not.toBeNull();
        expect(split()!.contains(top())).toBe(true);
        expect(split()!.contains(bottom())).toBe(true);
    });

    it('should show only the content, without a splitter, when there is no panel', () => {
        create({ hasBottom: false });

        expect(split()).toBeNull();
        expect(top()).not.toBeNull();
        expect(bottom()).toBeNull();
    });

    it('should start the content and the panel at the default split', () => {
        create();

        expect(splitter().componentInstance.panelSizes).toEqual([68, 32]);
    });

    it('should give a collapsed panel almost no share, and mark it so the line is hidden', () => {
        create({ expanded: false });

        // Not 0, which PrimeNG would read as "no size given" and split evenly
        expect(splitter().componentInstance.panelSizes[1]).toBeGreaterThan(0);
        expect(splitter().componentInstance.panelSizes[1]).toBeLessThan(1);
        expect(split()!.classList).toContain('feedback-split--collapsed');
    });

    it('should open a collapsed panel at the height the user had dragged it to', () => {
        create();
        splitter().triggerEventHandler('onResizeEnd', { originalEvent: new MouseEvent('mouseup'), sizes: [55, 45] });
        fixture.detectChanges();

        fixture.componentRef.setInput('expanded', false);
        fixture.detectChanges();
        expect(splitter().componentInstance.panelSizes[1]).toBeLessThan(1);

        fixture.componentRef.setInput('expanded', true);
        fixture.detectChanges();
        expect(splitter().componentInstance.panelSizes).toEqual([55, 45]);
    });

    it('should not take the share of a collapsed panel for a choice of the user', () => {
        create({ expanded: false });
        splitter().triggerEventHandler('onResizeEnd', { originalEvent: new MouseEvent('mouseup'), sizes: [99.99, 0.01] });

        fixture.componentRef.setInput('expanded', true);
        fixture.detectChanges();

        expect(splitter().componentInstance.panelSizes).toEqual([68, 32]);
    });

    it('should ask for the panel to collapse when it is dragged down to its smallest size', () => {
        create();
        splitter().triggerEventHandler('onResizeEnd', { originalEvent: new MouseEvent('mouseup'), sizes: [92, 8] });

        expect(fixture.componentInstance.collapseRequests).toBe(1);
    });

    it('should not ask for the panel to collapse while it is dragged to a usable height', () => {
        create();
        splitter().triggerEventHandler('onResizeEnd', { originalEvent: new MouseEvent('mouseup'), sizes: [80, 20] });

        expect(fixture.componentInstance.collapseRequests).toBe(0);
    });

    it('should reopen a panel that was dragged shut at the height it had before the drag, also after a reload', () => {
        create();
        splitter().triggerEventHandler('onResizeEnd', { originalEvent: new MouseEvent('mouseup'), sizes: [55, 45] });
        // The splitter stores the size of every drag itself
        localStorage.setItem(STORAGE_KEY, JSON.stringify([92, 8]));
        splitter().triggerEventHandler('onResizeEnd', { originalEvent: new MouseEvent('mouseup'), sizes: [92, 8] });

        fixture.componentRef.setInput('expanded', false);
        fixture.detectChanges();
        fixture.componentRef.setInput('expanded', true);
        fixture.detectChanges();

        expect(splitter().componentInstance.panelSizes).toEqual([55, 45]);
        expect(JSON.parse(localStorage.getItem(STORAGE_KEY)!)).toEqual([55, 45]);
    });

    it('should use the split saved from an earlier visit when the panel is opened again', () => {
        localStorage.setItem(STORAGE_KEY, JSON.stringify([40, 60]));
        create();

        fixture.componentRef.setInput('expanded', false);
        fixture.detectChanges();
        fixture.componentRef.setInput('expanded', true);
        fixture.detectChanges();

        expect(splitter().componentInstance.panelSizes).toEqual([40, 60]);
    });

    it('should ignore a saved split that is not a usable one', () => {
        localStorage.setItem(STORAGE_KEY, 'not json');
        create();
        expect(splitter().componentInstance.panelSizes).toEqual([68, 32]);

        fixture.destroy();
        localStorage.setItem(STORAGE_KEY, JSON.stringify([50, 0]));
        create();
        expect(splitter().componentInstance.panelSizes).toEqual([68, 32]);
    });

    it('should only limit the height, and never ask for a collapse, when dragging it shut is off', () => {
        create();
        fixture.componentRef.setInput('snapPercent', 0);
        fixture.detectChanges();
        splitter().triggerEventHandler('onResizeEnd', { originalEvent: new MouseEvent('mouseup'), sizes: [92, 8] });

        expect(fixture.componentInstance.collapseRequests).toBe(0);
    });
});
