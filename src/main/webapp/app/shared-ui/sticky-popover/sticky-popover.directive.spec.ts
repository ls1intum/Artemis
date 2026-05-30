import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Component, DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import { StickyPopoverDirective } from 'app/shared-ui/sticky-popover/sticky-popover.directive';

@Component({
    template: '<div [jhiStickyPopover]="content" placement="right" triggers="manual"></div><ng-template #content><span>some content</span></ng-template>',
    imports: [StickyPopoverDirective],
})
class StickyPopoverComponent {
    pattern: string;
}

describe('StickyPopoverDirective', () => {
    setupTestBed({ zoneless: true });
    let fixture: ComponentFixture<StickyPopoverComponent>;
    let debugDirective: DebugElement;
    let directive: StickyPopoverDirective;
    let openStub: ReturnType<typeof vi.spyOn>;

    beforeEach(() => {
        vi.useFakeTimers();
        return TestBed.configureTestingModule({
            imports: [StickyPopoverComponent],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(StickyPopoverComponent);
                debugDirective = fixture.debugElement.query(By.directive(StickyPopoverDirective));
                directive = debugDirective.injector.get(StickyPopoverDirective);
                openStub = vi.spyOn(directive, 'open');
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
        vi.useRealTimers();
    });

    it('should open on hover', () => {
        const div = fixture.debugElement.query(By.css('div'));
        expect(div).not.toBeNull();
        div.nativeElement.dispatchEvent(new MouseEvent('pointerenter'));
        vi.advanceTimersByTime(10);
        expect(openStub).toHaveBeenCalledOnce();
        expect(directive.isOpen()).toBeTruthy();
        const span = fixture.debugElement.query(By.css('span'));
        expect(span).not.toBeNull();
    });

    it('should display content on hover', () => {
        const div = fixture.debugElement.query(By.css('div'));
        expect(div).not.toBeNull();
        div.nativeElement.dispatchEvent(new MouseEvent('pointerenter'));
        vi.advanceTimersByTime(10);
        const span = fixture.debugElement.query(By.css('span'));
        expect(span).not.toBeNull();
    });

    it('should close on leave', async () => {
        const div = fixture.debugElement.query(By.css('div'));
        expect(div).not.toBeNull();
        div.nativeElement.dispatchEvent(new MouseEvent('pointerenter'));
        vi.advanceTimersByTime(10);
        let span = fixture.debugElement.query(By.css('span'));
        expect(span).not.toBeNull();
        div.nativeElement.dispatchEvent(new MouseEvent('pointerleave'));
        // Advance past the directive's 100ms close debounce, then drain the NgbPopover close animation
        // transition (timer + microtasks) so _windowRef is torn down. The original fakeAsync test relied
        // on tick(100) flushing both; the zoneless equivalent is the async fake-timer API.
        await vi.advanceTimersByTimeAsync(100);
        await vi.runAllTimersAsync();
        fixture.detectChanges();
        span = fixture.debugElement.query(By.css('span'));
        expect(span).toBeNull();
    });
});
