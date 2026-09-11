import { ApplicationRef, Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { vi } from 'vitest';
import { TumUiDisabledReasonDirective } from './tum-ui-disabled-reason.directive';

@Component({
    template: `<button [tumUiDisabledReason]="reason()" (click)="clicks = clicks + 1">Save</button>`,
    imports: [TumUiDisabledReasonDirective],
})
class DisabledReasonHostComponent {
    readonly reason = signal('Save the exercise first');
    clicks = 0;
}

describe('TumUiDisabledReasonDirective', () => {
    let fixture: ComponentFixture<DisabledReasonHostComponent>;
    let button: HTMLButtonElement;

    beforeEach(async () => {
        vi.useFakeTimers();
        await TestBed.configureTestingModule({ imports: [DisabledReasonHostComponent] }).compileComponents();
        fixture = TestBed.createComponent(DisabledReasonHostComponent);
        fixture.detectChanges();
        button = fixture.debugElement.query(By.css('button')).nativeElement;
    });

    afterEach(() => {
        vi.runOnlyPendingTimers();
        vi.useRealTimers();
    });

    it('marks the host aria-disabled while keeping it in the tab order', () => {
        expect(button.getAttribute('aria-disabled')).toBe('true');
        expect(button.hasAttribute('disabled')).toBe(false);
        expect(button.tabIndex).toBe(0);
    });

    it('swallows activation before the (click) handler runs and prevents the default action', () => {
        const event = new MouseEvent('click', { bubbles: true, cancelable: true });
        button.dispatchEvent(event);
        expect(fixture.componentInstance.clicks).toBe(0);
        expect(event.defaultPrevented).toBe(true);
    });

    it('shows the reason as a tooltip on focus', () => {
        button.dispatchEvent(new Event('focusin', { bubbles: true }));
        vi.advanceTimersByTime(200);
        TestBed.inject(ApplicationRef).tick();
        expect(document.querySelector('.tum-ui-tooltip-bubble')?.textContent).toContain('Save the exercise first');
        expect(button.getAttribute('aria-describedby')).toBeTruthy();
        button.dispatchEvent(new Event('focusout', { bubbles: true }));
        vi.advanceTimersByTime(200);
    });

    it('re-enables the host when the reason is cleared', () => {
        fixture.componentInstance.reason.set('');
        fixture.detectChanges();
        expect(button.getAttribute('aria-disabled')).toBeNull();
        button.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
        expect(fixture.componentInstance.clicks).toBe(1);
        button.dispatchEvent(new MouseEvent('mouseenter'));
        vi.advanceTimersByTime(200);
        expect(document.querySelector('.tum-ui-tooltip-bubble')).toBeNull();
    });
});
