import { ApplicationRef, Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { TumUiTooltipDirective } from './tum-ui-tooltip.directive';

@Component({
    template: `<button [tumUiTooltip]="text()" [showDelayMs]="0" [hideDelayMs]="0">Hover me</button>`,
    imports: [TumUiTooltipDirective],
})
class TooltipHostComponent {
    text = signal<string | readonly string[]>('Help text');
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

    function arrow(): HTMLElement | null {
        return bubble()?.querySelector('span[aria-hidden="true"]') ?? null;
    }

    describe('arrow placement', () => {
        // The overlay is created with withPush, so a bubble near the viewport edge is shoved sideways. An arrow
        // centred on the bubble then points beside the host instead of at it.
        function showWithGeometry(host: DOMRect, bubbleRect: DOMRect): void {
            vi.spyOn(button, 'getBoundingClientRect').mockReturnValue(host);
            button.dispatchEvent(new MouseEvent('mouseenter'));
            vi.advanceTimersByTime(1);
            vi.spyOn(bubble()!, 'getBoundingClientRect').mockReturnValue(bubbleRect);
        }

        it('points the arrow at the host when the bubble has been pushed sideways', () => {
            // Host centred on 520; the bubble was pushed left so it spans 110..670, whose middle is 390.
            showWithGeometry(new DOMRect(490, 100, 60, 30), new DOMRect(110, 40, 560, 60));
            button.dispatchEvent(new MouseEvent('mouseenter'));
            vi.advanceTimersByTime(1);

            // 520 - 110 = 410 from the bubble's left edge, not the 280 a centred arrow would use.
            expect(arrow()!.style.left).toBe('410px');
        });

        it('keeps the arrow clear of the corner when the host sits beyond the bubble', () => {
            // Host far to the right of a bubble that could not follow it.
            showWithGeometry(new DOMRect(900, 100, 60, 30), new DOMRect(110, 40, 200, 60));
            button.dispatchEvent(new MouseEvent('mouseenter'));
            vi.advanceTimersByTime(1);

            expect(arrow()!.style.left).toBe('188px');
        });
    });

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

    it('stays visible while the pointer moves from the trigger into the tooltip', () => {
        button.dispatchEvent(new MouseEvent('mouseenter'));
        vi.advanceTimersByTime(1);
        const tooltip = bubble()!;

        button.dispatchEvent(new MouseEvent('mouseleave'));
        tooltip.dispatchEvent(new MouseEvent('mouseenter'));
        vi.advanceTimersByTime(1);
        expect(bubble()).toBe(tooltip);

        tooltip.dispatchEvent(new MouseEvent('mouseleave'));
        vi.advanceTimersByTime(1);
        expect(bubble()).toBeNull();
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
        button.dispatchEvent(new MouseEvent('mouseleave'));
        vi.advanceTimersByTime(1);
        expect(bubble()).not.toBeNull();
        button.dispatchEvent(new Event('focusout', { bubbles: true }));
        vi.advanceTimersByTime(1);
        expect(bubble()).toBeNull();
    });

    it('stays visible when focus leaves but the mouse is still hovering', () => {
        button.dispatchEvent(new Event('focusin', { bubbles: true }));
        button.dispatchEvent(new MouseEvent('mouseenter'));
        vi.advanceTimersByTime(1);
        expect(bubble()).not.toBeNull();
        button.dispatchEvent(new Event('focusout', { bubbles: true }));
        vi.advanceTimersByTime(1);
        expect(bubble()).not.toBeNull();
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
        fixture.componentInstance.text.set('Updated help text');
        appRef.tick();
        appRef.tick();
        expect(bubble()?.textContent).toContain('Updated help text');
    });

    it('hides the tooltip when the content is cleared to empty while shown', () => {
        const appRef = TestBed.inject(ApplicationRef);
        button.dispatchEvent(new MouseEvent('mouseenter'));
        vi.advanceTimersByTime(1);
        appRef.tick();
        expect(bubble()).not.toBeNull();
        expect(button.getAttribute('aria-describedby')).toBeTruthy();
        fixture.componentInstance.text.set('');
        appRef.tick();
        expect(bubble()).toBeNull();
        expect(button.getAttribute('aria-describedby')).toBeNull();
    });

    it('renders several reasons as a list and clamps wider than the one-line form', () => {
        const appRef = TestBed.inject(ApplicationRef);
        fixture.componentInstance.text.set(['First reason', 'Second reason']);
        fixture.detectChanges();
        button.dispatchEvent(new MouseEvent('mouseenter'));
        vi.advanceTimersByTime(1);
        appRef.tick();

        const items = Array.from(bubble()!.querySelectorAll('li')).map((item) => item.textContent?.trim());
        expect(items).toEqual(['First reason', 'Second reason']);
        expect(bubble()!.className).toContain('max-w-100');
    });

    it('stays hidden when the content is an empty list', () => {
        fixture.componentInstance.text.set([]);
        fixture.detectChanges();
        button.dispatchEvent(new MouseEvent('mouseenter'));
        vi.advanceTimersByTime(1);

        expect(bubble()).toBeNull();
        expect(button.getAttribute('aria-describedby')).toBeNull();
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
