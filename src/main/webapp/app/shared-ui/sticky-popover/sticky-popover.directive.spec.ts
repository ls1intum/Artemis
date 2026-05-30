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
