import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { TumAetUiPopoverComponent } from './tumaet-ui-popover.component';
import { TumAetUiPopoverTriggerDirective } from './tumaet-ui-popover-trigger.directive';

@Component({
    template: `
        <button [tumAetUiPopoverTrigger]="pop" data-testid="trigger">Open</button>
        <tumaet-ui-popover #pop ariaLabel="Test popover">Panel content</tumaet-ui-popover>
    `,
    imports: [TumAetUiPopoverTriggerDirective, TumAetUiPopoverComponent],
})
class PopoverTriggerHostComponent {}

describe('TumAetUiPopoverTriggerDirective', () => {
    let fixture: ComponentFixture<PopoverTriggerHostComponent>;
    let button: HTMLButtonElement;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [PopoverTriggerHostComponent] }).compileComponents();
        fixture = TestBed.createComponent(PopoverTriggerHostComponent);
        fixture.detectChanges();
        button = fixture.debugElement.query(By.css('button')).nativeElement;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('exposes aria-haspopup and a collapsed aria-expanded initially', () => {
        expect(button.getAttribute('aria-haspopup')).toBe('dialog');
        expect(button.getAttribute('aria-expanded')).toBe('false');
    });

    it('opens the popover on click and reflects aria-expanded', () => {
        button.click();
        fixture.detectChanges();
        expect(button.getAttribute('aria-expanded')).toBe('true');
    });

    it('ignores hover and focus, which only open a hover popover', () => {
        button.dispatchEvent(new Event('pointerenter'));
        button.dispatchEvent(new Event('focus'));
        fixture.detectChanges();
        expect(button.getAttribute('aria-expanded')).toBe('false');
    });

    it('toggles the popover closed on a second click', () => {
        button.click();
        fixture.detectChanges();
        button.click();
        fixture.detectChanges();
        expect(button.getAttribute('aria-expanded')).toBe('false');
    });
});

@Component({
    template: `
        <button [tumAetUiPopoverTrigger]="pop" data-testid="trigger" (click)="clicks = clicks + 1">Theme</button>
        <button data-testid="next">Next</button>
        <tumaet-ui-popover #pop openOn="hover" ariaLabel="Theme options">
            <button data-testid="first">First</button>
            <button data-testid="last">Last</button>
        </tumaet-ui-popover>
    `,
    imports: [TumAetUiPopoverTriggerDirective, TumAetUiPopoverComponent],
})
class HoverPopoverHostComponent {
    clicks = 0;
}

describe('TumAetUiPopoverTriggerDirective with a hover popover', () => {
    let fixture: ComponentFixture<HoverPopoverHostComponent>;
    let trigger: HTMLButtonElement;

    const panel = () => document.querySelector<HTMLElement>('[role="dialog"]');
    const inPanel = (testId: string) => panel()!.querySelector<HTMLButtonElement>(`[data-testid="${testId}"]`)!;
    const isOpen = () => trigger.getAttribute('aria-expanded') === 'true';
    const tab = (target: HTMLElement, shiftKey = false) => {
        const event = new KeyboardEvent('keydown', { key: 'Tab', shiftKey, bubbles: true, cancelable: true });
        target.dispatchEvent(event);
        return event;
    };

    beforeEach(async () => {
        vi.useFakeTimers();
        await TestBed.configureTestingModule({ imports: [HoverPopoverHostComponent] }).compileComponents();
        fixture = TestBed.createComponent(HoverPopoverHostComponent);
        // Attached, so that focus can move between the trigger and the panel.
        document.body.appendChild(fixture.nativeElement);
        fixture.detectChanges();
        trigger = fixture.nativeElement.querySelector('[data-testid="trigger"]');
    });

    afterEach(() => {
        fixture.destroy();
        fixture.nativeElement.remove();
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    function hover(): void {
        trigger.dispatchEvent(new Event('pointerenter'));
        fixture.detectChanges();
    }

    it('opens when the pointer enters the trigger, without a backdrop and without claiming the page', () => {
        hover();

        expect(isOpen()).toBe(true);
        expect(panel()!.getAttribute('aria-label')).toBe('Theme options');
        expect(panel()!.hasAttribute('aria-modal')).toBe(false);
        expect(document.querySelector('.cdk-overlay-backdrop')).toBeNull();
        expect(panel()!.contains(document.activeElement)).toBe(false);
    });

    it('opens when the trigger receives keyboard focus', () => {
        trigger.focus();
        fixture.detectChanges();

        expect(isOpen()).toBe(true);
    });

    it('leaves a click to the trigger and stays open', () => {
        hover();

        trigger.click();
        fixture.detectChanges();

        expect(fixture.componentInstance.clicks).toBe(1);
        expect(isOpen()).toBe(true);
    });

    it('closes shortly after the pointer left the trigger', () => {
        hover();

        trigger.dispatchEvent(new Event('pointerleave'));
        vi.advanceTimersByTime(100);
        fixture.detectChanges();
        expect(isOpen()).toBe(true);

        vi.advanceTimersByTime(200);
        fixture.detectChanges();
        expect(isOpen()).toBe(false);
    });

    it('stays open when the pointer moves from the trigger onto the panel', () => {
        hover();

        trigger.dispatchEvent(new Event('pointerleave'));
        panel()!.dispatchEvent(new Event('pointerenter'));
        vi.advanceTimersByTime(1000);
        fixture.detectChanges();
        expect(isOpen()).toBe(true);

        panel()!.dispatchEvent(new Event('pointerleave'));
        vi.advanceTimersByTime(300);
        fixture.detectChanges();
        expect(isOpen()).toBe(false);
    });

    it('stays open while a control of the panel has keyboard focus, although the pointer left', () => {
        hover();
        trigger.dispatchEvent(new Event('pointerleave'));
        panel()!.dispatchEvent(new Event('pointerenter'));
        inPanel('first').focus();

        panel()!.dispatchEvent(new Event('pointerleave'));
        vi.advanceTimersByTime(1000);
        fixture.detectChanges();
        expect(isOpen()).toBe(true);
        expect(document.activeElement).toBe(inPanel('first'));

        fixture.nativeElement.querySelector('[data-testid="next"]').focus();
        vi.advanceTimersByTime(300);
        fixture.detectChanges();
        expect(isOpen()).toBe(false);
    });

    it('stays open when focus leaves the panel while the pointer is still over it', () => {
        hover();
        trigger.dispatchEvent(new Event('pointerleave'));
        panel()!.dispatchEvent(new Event('pointerenter'));
        inPanel('first').focus();

        fixture.nativeElement.querySelector('[data-testid="next"]').focus();
        vi.advanceTimersByTime(1000);
        fixture.detectChanges();
        expect(isOpen()).toBe(true);
    });

    it('stays open while the trigger has keyboard focus, although the pointer left it', () => {
        // The test environment does not implement `:focus-visible`, which tells keyboard focus from a click.
        vi.spyOn(trigger, 'matches').mockImplementation((selector: string) => selector === ':focus-visible');
        trigger.focus();
        hover();

        trigger.dispatchEvent(new Event('pointerleave'));
        vi.advanceTimersByTime(1000);
        fixture.detectChanges();
        expect(isOpen()).toBe(true);

        fixture.nativeElement.querySelector('[data-testid="next"]').focus();
        vi.advanceTimersByTime(300);
        fixture.detectChanges();
        expect(isOpen()).toBe(false);
    });

    it('stays open when focus leaves the trigger while the pointer is still over it', () => {
        trigger.focus();
        hover();

        fixture.nativeElement.querySelector('[data-testid="next"]').focus();
        vi.advanceTimersByTime(1000);
        fixture.detectChanges();
        expect(isOpen()).toBe(true);

        trigger.dispatchEvent(new Event('pointerleave'));
        vi.advanceTimersByTime(300);
        fixture.detectChanges();
        expect(isOpen()).toBe(false);
    });

    it('closes after the pointer left a trigger that only holds focus from a click', () => {
        trigger.focus();
        hover();
        vi.spyOn(trigger, 'matches').mockImplementation((selector: string) => selector !== ':focus-visible');

        trigger.dispatchEvent(new Event('pointerleave'));
        vi.advanceTimersByTime(300);
        fixture.detectChanges();
        expect(isOpen()).toBe(false);
    });

    it('stays open after a finger that opened it was lifted, until a tap elsewhere', () => {
        trigger.dispatchEvent(new PointerEvent('pointerenter', { pointerType: 'touch' }));
        fixture.detectChanges();
        expect(isOpen()).toBe(true);

        trigger.dispatchEvent(new PointerEvent('pointerleave', { pointerType: 'touch' }));
        vi.advanceTimersByTime(1000);
        fixture.detectChanges();
        expect(isOpen()).toBe(true);

        // Touching the panel and lifting the finger again, and the trigger losing focus, do not close it either.
        panel()!.dispatchEvent(new PointerEvent('pointerenter', { pointerType: 'touch' }));
        panel()!.dispatchEvent(new PointerEvent('pointerleave', { pointerType: 'touch' }));
        trigger.dispatchEvent(new Event('blur'));
        vi.advanceTimersByTime(1000);
        fixture.detectChanges();
        expect(isOpen()).toBe(true);

        document.body.click();
        fixture.detectChanges();
        expect(isOpen()).toBe(false);
    });

    it('follows the mouse again after a popover that was opened by touch has been dismissed', () => {
        trigger.dispatchEvent(new PointerEvent('pointerenter', { pointerType: 'touch' }));
        fixture.detectChanges();
        document.body.click();
        fixture.detectChanges();

        hover();
        trigger.dispatchEvent(new Event('pointerleave'));
        vi.advanceTimersByTime(300);
        fixture.detectChanges();
        expect(isOpen()).toBe(false);
    });

    it('closes on a click elsewhere, but not on a click inside the panel', () => {
        hover();

        inPanel('first').click();
        fixture.detectChanges();
        expect(isOpen()).toBe(true);

        document.body.click();
        fixture.detectChanges();
        expect(isOpen()).toBe(false);
    });

    it('closes on Escape and opens again on the next hover', () => {
        hover();

        document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
        fixture.detectChanges();
        expect(isOpen()).toBe(false);

        hover();
        expect(isOpen()).toBe(true);
    });

    it('hands the focus back to the trigger when Escape closes it from inside the panel', () => {
        trigger.focus();
        fixture.detectChanges();
        inPanel('first').focus();

        inPanel('first').dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
        fixture.detectChanges();

        expect(isOpen()).toBe(false);
        expect(document.activeElement).toBe(trigger);
    });

    it('moves focus from the trigger into the panel on Tab', () => {
        trigger.focus();
        fixture.detectChanges();

        const event = tab(trigger);

        expect(event.defaultPrevented).toBe(true);
        expect(document.activeElement).toBe(inPanel('first'));
        vi.advanceTimersByTime(1000);
        fixture.detectChanges();
        expect(isOpen()).toBe(true);
    });

    it('keeps Tab inside the panel until its last control', () => {
        trigger.focus();
        fixture.detectChanges();
        inPanel('first').focus();

        const event = tab(inPanel('first'));

        expect(event.defaultPrevented).toBe(false);
        expect(isOpen()).toBe(true);
    });

    it('closes on Tab from the last control and lets the browser continue after the trigger', () => {
        trigger.focus();
        fixture.detectChanges();
        inPanel('last').focus();

        const event = tab(inPanel('last'));
        fixture.detectChanges();

        // Not prevented: with the focus back on the trigger, the browser moves on to the element after it.
        expect(event.defaultPrevented).toBe(false);
        expect(document.activeElement).toBe(trigger);
        expect(isOpen()).toBe(false);
    });

    it('returns to the trigger on Shift+Tab from the first control', () => {
        trigger.focus();
        fixture.detectChanges();
        inPanel('first').focus();

        const event = tab(inPanel('first'), true);
        fixture.detectChanges();

        expect(event.defaultPrevented).toBe(true);
        expect(document.activeElement).toBe(trigger);
        expect(isOpen()).toBe(false);
    });
});
