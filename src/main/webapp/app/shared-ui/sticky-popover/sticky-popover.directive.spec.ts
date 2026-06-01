import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import { StickyPopoverDirective } from 'app/shared-ui/sticky-popover/sticky-popover.directive';
import { vi } from 'vitest';

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

        TestBed.configureTestingModule({
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
        // Tear the component (and its real NgbPopover/Popper instance) down deterministically while
        // fake timers are still active and the host element is still attached, then flush any pending
        // close timer. Doing the teardown here — instead of leaving it to Angular's destroyAfterEach
        // after real timers are restored — avoids an environment-dependent
        // `scrollParent.removeEventListener is not a function` unhandled error from Popper's listener
        // cleanup that otherwise makes the shared-ui Vitest run exit non-zero (assertions still pass).
        fixture?.destroy();
        vi.runOnlyPendingTimers();
        vi.restoreAllMocks();
        vi.useRealTimers();
    });

    it('should open on hover', () => {
        const div = fixture.debugElement.query(By.css('div'));
        expect(div).not.toBeNull();
        div.nativeElement.dispatchEvent(new MouseEvent('pointerenter'));
        vi.advanceTimersByTime(0);
        fixture.detectChanges();
        expect(openStub).toHaveBeenCalledOnce();
        expect(directive.isOpen()).toBeTruthy();
        const span = fixture.debugElement.query(By.css('span'));
        expect(span).not.toBeNull();
    });

    it('should display content on hover', () => {
        const div = fixture.debugElement.query(By.css('div'));
        expect(div).not.toBeNull();
        div.nativeElement.dispatchEvent(new MouseEvent('pointerenter'));
        vi.advanceTimersByTime(0);
        fixture.detectChanges();
        const span = fixture.debugElement.query(By.css('span'));
        expect(span).not.toBeNull();
    });

    it('should close on leave', () => {
        const div = fixture.debugElement.query(By.css('div'));
        expect(div).not.toBeNull();
        div.nativeElement.dispatchEvent(new MouseEvent('pointerenter'));
        vi.advanceTimersByTime(0);
        fixture.detectChanges();
        const span = fixture.debugElement.query(By.css('span'));
        expect(span).not.toBeNull();
        const closeStub = vi.spyOn(directive, 'close');
        div.nativeElement.dispatchEvent(new MouseEvent('pointerleave'));
        vi.advanceTimersByTime(100);
        fixture.detectChanges();
        expect(closeStub).toHaveBeenCalledOnce();
    });
});
