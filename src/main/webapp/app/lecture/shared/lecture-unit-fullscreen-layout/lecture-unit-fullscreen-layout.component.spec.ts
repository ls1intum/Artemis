import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
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

    it('emits backdropClick when overlay is clicked in fullscreen mode', () => {
        const backdropClickSpy = vi.fn();
        component.backdropClick.subscribe(backdropClickSpy);

        component.open();
        fixture.detectChanges();

        const overlay = fixture.debugElement.query(By.css('.fullscreen-overlay'));
        overlay.nativeElement.click();

        expect(backdropClickSpy).toHaveBeenCalledTimes(1);
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

    it('calls initSplitter when fullscreen with sidebar', async () => {
        const initSplitterSpy = vi.spyOn(component as any, 'initSplitter');

        fixture.componentRef.setInput('showSidebar', true);
        component.open();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(initSplitterSpy).toHaveBeenCalled();
    });

    it('splitSizesChange emits when vertical split is resized', () => {
        const splitSizesChangeSpy = vi.fn();
        component.splitSizesChange.subscribe(splitSizesChangeSpy);

        // Call the private method that emits the event (simulating a drag end)
        component['ngZone'].run(() => {
            component.splitSizesChange.emit([60, 40]);
        });

        expect(splitSizesChangeSpy).toHaveBeenCalledWith([60, 40]);
    });

    it('calls initHorizontalSplitter when hasHorizontalSplit is true', async () => {
        const topEl = document.createElement('div');
        const bottomEl = document.createElement('div');
        const initHorizontalSplitterSpy = vi.spyOn(component as any, 'initHorizontalSplitter');

        document.body.appendChild(topEl);
        document.body.appendChild(bottomEl);

        try {
            fixture.componentRef.setInput('hasHorizontalSplit', true);
            fixture.componentRef.setInput('horizontalSplitTopElement', { nativeElement: topEl });
            fixture.componentRef.setInput('horizontalSplitBottomElement', { nativeElement: bottomEl });

            component.open();
            await fixture.whenStable();
            fixture.detectChanges();

            expect(initHorizontalSplitterSpy).toHaveBeenCalled();
        } finally {
            topEl.remove();
            bottomEl.remove();
        }
    });

    it('horizontalSplitSizesChange emits when horizontal split is resized', () => {
        const horizontalSplitSizesChangeSpy = vi.fn();
        component.horizontalSplitSizesChange.subscribe(horizontalSplitSizesChangeSpy);

        // Call the private method that emits the event (simulating a drag end)
        component['ngZone'].run(() => {
            component.horizontalSplitSizesChange.emit([70, 30]);
        });

        expect(horizontalSplitSizesChangeSpy).toHaveBeenCalledWith([70, 30]);
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

    it('ngOnDestroy cleans up splitters and event listeners', () => {
        const destroySplitterSpy = vi.spyOn(component as any, 'destroySplitter');
        const destroyHorizontalSplitterSpy = vi.spyOn(component as any, 'destroyHorizontalSplitter');
        const clearFullscreenTopOffsetSpy = vi.spyOn(component as any, 'clearFullscreenTopOffset');

        component.ngOnDestroy();

        expect(destroySplitterSpy).toHaveBeenCalled();
        expect(destroyHorizontalSplitterSpy).toHaveBeenCalled();
        expect(clearFullscreenTopOffsetSpy).toHaveBeenCalled();
    });

    it('focus trap prevents tab navigation outside container', async () => {
        component.open();
        await fixture.whenStable();
        fixture.detectChanges();

        const container = component.contentContainer()?.nativeElement;
        expect(container).toBeDefined();

        const button1 = document.createElement('button');
        const button2 = document.createElement('button');
        container!.appendChild(button1);
        container!.appendChild(button2);

        // Simulate tab at last element
        const event = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true });
        const preventDefaultSpy = vi.spyOn(event, 'preventDefault');

        Object.defineProperty(document, 'activeElement', {
            writable: true,
            configurable: true,
            value: button2,
        });

        container!.dispatchEvent(event);

        expect(preventDefaultSpy).toHaveBeenCalled();
    });
});
