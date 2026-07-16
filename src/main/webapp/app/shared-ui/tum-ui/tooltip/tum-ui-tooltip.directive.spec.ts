import { ApplicationRef, Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { TumUiTooltipDirective } from 'app/shared-ui/tum-ui/tooltip/tum-ui-tooltip.directive';

@Component({
    template: `<button [tumUiTooltip]="text()" [showDelay]="0" [hideDelay]="0">Hover me</button>`,
    imports: [TumUiTooltipDirective],
})
class TooltipHostComponent {
    text = signal('Help text');
}

describe('TumUiTooltipDirective', () => {
    let fixture: ComponentFixture<TooltipHostComponent>;
    let button: HTMLButtonElement;

    beforeEach(async () => {
        vi.useFakeTimers();
        await TestBed.configureTestingModule({ imports: [TooltipHostComponent] }).compileComponents();
        fixture = TestBed.createComponent(TooltipHostComponent);
        fixture.detectChanges();
        button = fixture.debugElement.query(By.css('button')).nativeElement;
    });

    afterEach(() => {
        vi.runOnlyPendingTimers();
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    function bubble(): HTMLElement | null {
        return document.querySelector('.tum-ui-tooltip-bubble');
    }

    it('attaches the tooltip overlay and wires aria-describedby on mouseenter', () => {
        button.dispatchEvent(new MouseEvent('mouseenter'));
        vi.advanceTimersByTime(1);
        expect(bubble()).not.toBeNull();
        expect(button.getAttribute('aria-describedby')).toBeTruthy();
    });

    it('removes the tooltip and aria-describedby on mouseleave', () => {
        button.dispatchEvent(new MouseEvent('mouseenter'));
        vi.advanceTimersByTime(1);
        button.dispatchEvent(new MouseEvent('mouseleave'));
        vi.advanceTimersByTime(1);
        expect(bubble()).toBeNull();
        expect(button.getAttribute('aria-describedby')).toBeNull();
    });

    it('hides the tooltip on Escape', () => {
        button.dispatchEvent(new MouseEvent('mouseenter'));
        vi.advanceTimersByTime(1);
        button.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
        expect(bubble()).toBeNull();
    });

    it('does not stack overlays when mouseenter and focusin arrive within the show delay', () => {
        button.dispatchEvent(new MouseEvent('mouseenter'));
        button.dispatchEvent(new Event('focusin', { bubbles: true }));
        vi.advanceTimersByTime(1);
        expect(document.querySelectorAll('.tum-ui-tooltip-bubble')).toHaveLength(1);
    });

    it('stays visible when the mouse leaves but keyboard focus is still active', () => {
        button.dispatchEvent(new MouseEvent('mouseenter'));
        button.dispatchEvent(new Event('focusin', { bubbles: true }));
        vi.advanceTimersByTime(1);
        expect(bubble()).not.toBeNull();
        // Mouse leaves, but the element remains focused: the tooltip must stay.
        button.dispatchEvent(new MouseEvent('mouseleave'));
        vi.advanceTimersByTime(1);
        expect(bubble()).not.toBeNull();
        // Focus finally leaves too: now it hides.
        button.dispatchEvent(new Event('focusout', { bubbles: true }));
        vi.advanceTimersByTime(1);
        expect(bubble()).toBeNull();
    });

    it('stays visible when focus leaves but the mouse is still hovering', () => {
        button.dispatchEvent(new Event('focusin', { bubbles: true }));
        button.dispatchEvent(new MouseEvent('mouseenter'));
        vi.advanceTimersByTime(1);
        expect(bubble()).not.toBeNull();
        // Blur, but the mouse is still over the element: the tooltip must stay.
        button.dispatchEvent(new Event('focusout', { bubbles: true }));
        vi.advanceTimersByTime(1);
        expect(bubble()).not.toBeNull();
        // Mouse finally leaves too: now it hides.
        button.dispatchEvent(new MouseEvent('mouseleave'));
        vi.advanceTimersByTime(1);
        expect(bubble()).toBeNull();
    });

    it('updates the visible tooltip text when the content input changes while shown', () => {
        const appRef = TestBed.inject(ApplicationRef);
        button.dispatchEvent(new MouseEvent('mouseenter'));
        vi.advanceTimersByTime(1);
        appRef.tick();
        expect(bubble()?.textContent).toContain('Help text');
        // Change the bound content while the bubble is open; the directive's effect must push the new
        // text into the live content component (regression: it previously never tracked content()).
        fixture.componentInstance.text.set('Updated help text');
        appRef.tick(); // propagate the input + flush the sync effect (setInput on the content component)
        appRef.tick(); // render the content view with the pushed text
        expect(bubble()?.textContent).toContain('Updated help text');
    });

    it('preserves a pre-existing aria-describedby token and restores it on hide', () => {
        button.setAttribute('aria-describedby', 'external-desc');
        button.dispatchEvent(new MouseEvent('mouseenter'));
        vi.advanceTimersByTime(1);
        expect(button.getAttribute('aria-describedby')).toContain('external-desc');
        expect(button.getAttribute('aria-describedby')?.split(' ').length).toBe(2);
        button.dispatchEvent(new MouseEvent('mouseleave'));
        vi.advanceTimersByTime(1);
        expect(button.getAttribute('aria-describedby')).toBe('external-desc');
    });
});
