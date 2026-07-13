import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { TumUiTooltipDirective } from 'app/shared-ui/tum-ui/tooltip/tum-ui-tooltip.directive';

@Component({
    template: `<button [tumUiTooltip]="text" [showDelay]="0" [hideDelay]="0">Hover me</button>`,
    imports: [TumUiTooltipDirective],
})
class TooltipHostComponent {
    text = 'Help text';
}

describe('TumUiTooltipDirective', () => {
    setupTestBed({ zoneless: true });

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
