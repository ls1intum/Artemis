import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LectureUnitFullscreenLayoutComponent } from './lecture-unit-fullscreen-layout.component';

describe('LectureUnitFullscreenLayoutComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<LectureUnitFullscreenLayoutComponent>;
    let component: LectureUnitFullscreenLayoutComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LectureUnitFullscreenLayoutComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(LectureUnitFullscreenLayoutComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('isCollapsed', false);
        fixture.detectChanges();
    });

    afterEach(() => {
        document.body.classList.remove('lecture-combined-view-fullscreen-active');
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('setBackgroundInert keeps sticky navbar interactive in fullscreen', () => {
        const host = fixture.nativeElement as HTMLElement;
        const wrapper = document.createElement('div');
        const navbar = document.createElement('div');
        navbar.className = 'sticky-top-navbar';
        wrapper.append(navbar, host);
        document.body.appendChild(wrapper);

        try {
            component['setBackgroundInert'](true);

            expect(navbar.hasAttribute('inert')).toBe(false);
            expect(navbar.hasAttribute('aria-hidden')).toBe(false);
        } finally {
            component['setBackgroundInert'](false);
            wrapper.remove();
        }
    });

    it('renders the vertical p-splitter when fullscreen with sidebar', async () => {
        fixture.componentRef.setInput('showSidebar', true);
        component.open();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(component.showVerticalSplitter()).toBe(true);
        const splitter = (fixture.nativeElement as HTMLElement).querySelector('p-splitter.p-splitter-horizontal');
        expect(splitter).toBeTruthy();
    });

    it('does not render the vertical p-splitter without sidebar', async () => {
        component.open();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(component.showVerticalSplitter()).toBe(false);
        expect((fixture.nativeElement as HTMLElement).querySelector('p-splitter')).toBeNull();
    });

    it('renders the horizontal p-splitter when horizontal split is enabled', async () => {
        fixture.componentRef.setInput('horizontalSplit', {
            enabled: true,
            sizes: [50, 50],
            minSizes: [80, 80],
            defaultSizes: [50, 50],
        });

        component.open();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(component.showHorizontalSplitter()).toBe(true);
        const splitter = (fixture.nativeElement as HTMLElement).querySelector('p-splitter.p-splitter-vertical');
        expect(splitter).toBeTruthy();
    });

    it('nests the horizontal splitter inside the vertical splitter when both are active', async () => {
        fixture.componentRef.setInput('showSidebar', true);
        fixture.componentRef.setInput('horizontalSplit', {
            enabled: true,
            sizes: [50, 50],
            minSizes: [80, 80],
            defaultSizes: [50, 50],
        });

        component.open();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(component.showVerticalSplitter()).toBe(true);
        expect(component.showHorizontalSplitter()).toBe(true);

        const host = fixture.nativeElement as HTMLElement;
        const outer = host.querySelector('p-splitter.p-splitter-horizontal');
        const inner = host.querySelector('p-splitter.p-splitter-vertical');
        expect(outer).toBeTruthy();
        expect(inner).toBeTruthy();
        // The inner (video|pdf) splitter lives inside the outer (main|sidebar) splitter's first panel.
        expect(outer!.contains(inner!)).toBe(true);
    });

    it('onVerticalResize emits splitSizesChange with numeric sizes', () => {
        const splitSizesChangeSpy = vi.fn();
        component.splitSizesChange.subscribe(splitSizesChangeSpy);

        component['onVerticalResize']({ sizes: [60, '40'] });

        expect(splitSizesChangeSpy).toHaveBeenCalledWith([60, 40]);
    });

    it('onHorizontalResize emits horizontalSplitSizesChange with numeric sizes', () => {
        const horizontalSplitSizesChangeSpy = vi.fn();
        component.horizontalSplitSizesChange.subscribe(horizontalSplitSizesChangeSpy);

        component['onHorizontalResize']({ sizes: ['70', 30] });

        expect(horizontalSplitSizesChangeSpy).toHaveBeenCalledWith([70, 30]);
    });

    it('shows a drag overlay on splitter resize start and removes it on resize end', () => {
        const overlaySelector = 'div[style*="z-index: 10000"]';

        // Vertical (main | sidebar) splitter: overlay with a col-resize cursor.
        component['onSplitterResizeStart']('horizontal');
        let overlay = document.body.querySelector<HTMLElement>(overlaySelector);
        expect(overlay).not.toBeNull();
        expect(overlay!.style.cursor).toBe('col-resize');
        expect(overlay!.style.position).toBe('fixed');

        component['onVerticalResize']({ sizes: [60, 40] });
        expect(document.body.querySelector(overlaySelector)).toBeNull();

        // Horizontal (top | bottom) splitter: overlay with a row-resize cursor.
        component['onSplitterResizeStart']('vertical');
        overlay = document.body.querySelector<HTMLElement>(overlaySelector);
        expect(overlay).not.toBeNull();
        expect(overlay!.style.cursor).toBe('row-resize');

        component['onHorizontalResize']({ sizes: [50, 50] });
        expect(document.body.querySelector(overlaySelector)).toBeNull();
    });

    it('removes a lingering drag overlay when destroyed mid-resize', () => {
        component['onSplitterResizeStart']('horizontal');
        expect(document.body.querySelector('div[style*="z-index: 10000"]')).not.toBeNull();

        fixture.destroy();
        expect(document.body.querySelector('div[style*="z-index: 10000"]')).toBeNull();
    });

    it('converts px minSizes to percentages', () => {
        // 220px / 1600px reference width => 14% (rounded); see toPercentMinSizes.
        fixture.componentRef.setInput('verticalSplit', {
            sizes: [50, 50],
            minSizes: [220, 220],
            defaultSizes: [50, 50],
        });
        fixture.detectChanges();

        expect(component.verticalMinSizes()).toEqual([14, 14]);

        // 140px / 900px reference height => 16% (rounded).
        fixture.componentRef.setInput('horizontalSplit', {
            enabled: true,
            sizes: [50, 50],
            minSizes: [140, 140],
            defaultSizes: [50, 50],
        });
        fixture.detectChanges();

        expect(component.horizontalMinSizes()).toEqual([16, 16]);
    });

    it('window resize updates top offset in fullscreen', () => {
        const setPropertySpy = vi.spyOn(component['hostElement'].nativeElement.style, 'setProperty');

        component.open();
        fixture.detectChanges();

        component.onResize();

        expect(setPropertySpy).toHaveBeenCalledWith('--lecture-combined-view-top', expect.stringContaining('px'));
    });

    it('escape key closes fullscreen', () => {
        component.open();
        fixture.detectChanges();

        const event = new KeyboardEvent('keydown', { key: 'Escape' });
        const preventDefaultSpy = vi.spyOn(event, 'preventDefault');
        const stopPropagationSpy = vi.spyOn(event, 'stopPropagation');

        component.onEscapePressed(event);

        expect(preventDefaultSpy).toHaveBeenCalled();
        expect(stopPropagationSpy).toHaveBeenCalled();
        expect(component.isFullscreen()).toBe(false);
    });

    it('escape key respects preventEscapeClose flag', () => {
        fixture.componentRef.setInput('preventEscapeClose', true);
        component.open();
        fixture.detectChanges();

        const event = new KeyboardEvent('keydown', { key: 'Escape' });
        const preventDefaultSpy = vi.spyOn(event, 'preventDefault');

        component.onEscapePressed(event);

        expect(preventDefaultSpy).not.toHaveBeenCalled();
        expect(component.isFullscreen()).toBe(true);
    });

    it('close emits fullscreenChange with false', () => {
        const fullscreenChangeSpy = vi.fn();
        component.fullscreenChange.subscribe(fullscreenChangeSpy);

        component.open();
        fixture.detectChanges();

        fullscreenChangeSpy.mockClear();

        component.close();

        expect(fullscreenChangeSpy).toHaveBeenCalledWith(false);
        expect(component.isFullscreen()).toBe(false);
    });

    it('ngOnDestroy cleans up fullscreen state and event listeners', () => {
        const clearFullscreenTopOffsetSpy = vi.spyOn(component as any, 'clearFullscreenTopOffset');
        const cleanupFullscreenAccessibilitySpy = vi.spyOn(component as any, 'cleanupFullscreenAccessibility');

        component.ngOnDestroy();

        expect(clearFullscreenTopOffsetSpy).toHaveBeenCalled();
        expect(cleanupFullscreenAccessibilitySpy).toHaveBeenCalled();
    });

    it('focus trap wraps focus at both tab boundaries', async () => {
        component.open();
        await fixture.whenStable();
        fixture.detectChanges();

        const container = component.contentContainer()?.nativeElement;
        expect(container).toBeDefined();

        const button1 = document.createElement('button');
        const button2 = document.createElement('button');
        container!.appendChild(button1);
        container!.appendChild(button2);

        const setActiveElement = (element: HTMLElement) => {
            Object.defineProperty(document, 'activeElement', {
                writable: true,
                configurable: true,
                value: element,
            });
        };

        // Mock getFocusableElements to return the buttons so they survive filtering
        vi.spyOn(component as any, 'getFocusableElements').mockReturnValue([button1, button2]);
        const focusButton1Spy = vi.spyOn(button1, 'focus').mockImplementation(() => setActiveElement(button1));
        const focusButton2Spy = vi.spyOn(button2, 'focus').mockImplementation(() => setActiveElement(button2));

        // Test Tab wrapping: button2 -> button1
        const tabEvent = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true });
        const preventDefaultTabSpy = vi.spyOn(tabEvent, 'preventDefault');
        setActiveElement(button2);

        container!.dispatchEvent(tabEvent);

        expect(preventDefaultTabSpy).toHaveBeenCalled();
        expect(focusButton1Spy).toHaveBeenCalledOnce();
        expect(document.activeElement).toBe(button1);

        // Test Shift+Tab wrapping: button1 -> button2
        const shiftTabEvent = new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, bubbles: true });
        const preventDefaultShiftTabSpy = vi.spyOn(shiftTabEvent, 'preventDefault');
        setActiveElement(button1);

        container!.dispatchEvent(shiftTabEvent);

        expect(preventDefaultShiftTabSpy).toHaveBeenCalled();
        expect(focusButton2Spy).toHaveBeenCalledOnce();
        expect(document.activeElement).toBe(button2);
    });
});
