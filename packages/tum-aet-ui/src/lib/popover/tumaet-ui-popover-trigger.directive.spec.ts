import { Component, signal } from '@angular/core';
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

    it('can be destroyed before its first change detection, when the popover input has no value yet', () => {
        const untouched = TestBed.createComponent(PopoverTriggerHostComponent);

        expect(() => untouched.destroy()).not.toThrow();
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

    const touch = (target: HTMLElement, ...types: string[]) => types.forEach((type) => target.dispatchEvent(new PointerEvent(type, { pointerType: 'touch' })));

    it('opens on a tap and stays open after the finger was lifted, until a tap elsewhere', () => {
        touch(trigger, 'pointerenter', 'pointerdown');
        fixture.detectChanges();
        expect(isOpen()).toBe(false);

        touch(trigger, 'pointerup', 'pointerleave');
        trigger.click();
        vi.advanceTimersByTime(1000);
        fixture.detectChanges();
        expect(isOpen()).toBe(true);
        expect(fixture.componentInstance.clicks).toBe(1);

        // Touching the panel and lifting the finger again, and the trigger losing focus, do not close it either.
        touch(panel()!, 'pointerenter', 'pointerleave');
        trigger.dispatchEvent(new Event('blur'));
        vi.advanceTimersByTime(1000);
        fixture.detectChanges();
        expect(isOpen()).toBe(true);

        document.body.click();
        fixture.detectChanges();
        expect(isOpen()).toBe(false);
    });

    it('does not open when a finger on the trigger starts a scroll', () => {
        touch(trigger, 'pointerenter', 'pointerdown', 'pointercancel', 'pointerleave');
        vi.advanceTimersByTime(1000);
        fixture.detectChanges();

        expect(isOpen()).toBe(false);
    });

    it('does not open when a finger is dragged off the trigger and lifted without a click', () => {
        touch(trigger, 'pointerenter', 'pointerdown', 'pointerup', 'pointerleave');
        vi.advanceTimersByTime(1000);
        fixture.detectChanges();
        expect(isOpen()).toBe(false);

        // A later click that does not belong to that touch, such as Enter on the trigger, does not pin it either.
        const lateClick = new MouseEvent('click', { bubbles: true });
        Object.defineProperty(lateClick, 'timeStamp', { value: lateClick.timeStamp + 5000 });
        touch(trigger, 'pointerenter', 'pointerdown', 'pointerup', 'pointerleave');
        trigger.dispatchEvent(lateClick);
        fixture.detectChanges();
        expect(isOpen()).toBe(false);
    });

    it('follows the mouse again after a popover that was opened by a tap has been dismissed', () => {
        touch(trigger, 'pointerenter', 'pointerdown', 'pointerup', 'pointerleave');
        trigger.click();
        fixture.detectChanges();
        document.body.click();
        fixture.detectChanges();

        hover();
        expect(isOpen()).toBe(true);
        trigger.dispatchEvent(new Event('pointerleave'));
        vi.advanceTimersByTime(300);
        fixture.detectChanges();
        expect(isOpen()).toBe(false);
    });

    it('follows the mouse again when a listener closed a popover the moment a tap opened it', () => {
        const popover = fixture.debugElement.query(By.directive(TumAetUiPopoverComponent)).componentInstance as TumAetUiPopoverComponent;
        const changes: boolean[] = [];
        const subscription = popover.openChange.subscribe((open) => {
            changes.push(open);
            if (open) {
                popover.close();
            }
        });
        touch(trigger, 'pointerenter', 'pointerdown', 'pointerup', 'pointerleave');
        trigger.click();
        subscription.unsubscribe();
        fixture.detectChanges();
        expect(changes).toEqual([true, false]);
        expect(isOpen()).toBe(false);

        hover();
        expect(isOpen()).toBe(true);
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

    it('takes the closing panel out of the Tab order while it fades out', () => {
        const original = Element.prototype.animate;
        Element.prototype.animate = (() => ({ finished: new Promise<void>(() => {}) })) as unknown as typeof Element.prototype.animate;
        try {
            trigger.focus();
            fixture.detectChanges();
            inPanel('last').focus();
            const closingPanel = panel()!;

            tab(inPanel('last'));
            fixture.detectChanges();

            expect(closingPanel.isConnected).toBe(true);
            expect(closingPanel.closest('[inert]')).not.toBeNull();
            expect(closingPanel.contains(document.activeElement)).toBe(false);
        } finally {
            Element.prototype.animate = original;
        }
    });

    it('skips a control inside a disabled fieldset when Tab enters the panel', () => {
        const fieldset = document.createElement('fieldset');
        fieldset.disabled = true;
        const blocked = document.createElement('button');
        fieldset.appendChild(blocked);
        trigger.focus();
        fixture.detectChanges();
        panel()!.prepend(fieldset);

        const event = tab(trigger);

        expect(event.defaultPrevented).toBe(true);
        expect(document.activeElement).toBe(inPanel('first'));
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

@Component({
    template: `
        <button [tumAetUiPopoverTrigger]="pop" data-testid="trigger">Theme</button>
        <tumaet-ui-popover #pop openOn="hover" ariaLabel="Theme options">
            <button data-testid="later" tabindex="2">Later</button>
            <button data-testid="earlier" tabindex="1">Earlier</button>
            <button data-testid="unprioritized">Unprioritized</button>
        </tumaet-ui-popover>
    `,
    imports: [TumAetUiPopoverTriggerDirective, TumAetUiPopoverComponent],
})
class PositiveTabindexHostComponent {}

describe('TumAetUiPopoverTriggerDirective with positive tabindex values in a hover popover', () => {
    let fixture: ComponentFixture<PositiveTabindexHostComponent>;
    let trigger: HTMLButtonElement;

    const inPanel = (testId: string) => document.querySelector<HTMLElement>('[role="dialog"]')!.querySelector<HTMLButtonElement>(`[data-testid="${testId}"]`)!;
    const tab = (target: HTMLElement, shiftKey = false) => {
        const event = new KeyboardEvent('keydown', { key: 'Tab', shiftKey, bubbles: true, cancelable: true });
        target.dispatchEvent(event);
        return event;
    };

    beforeEach(async () => {
        vi.useFakeTimers();
        await TestBed.configureTestingModule({ imports: [PositiveTabindexHostComponent] }).compileComponents();
        fixture = TestBed.createComponent(PositiveTabindexHostComponent);
        document.body.appendChild(fixture.nativeElement);
        fixture.detectChanges();
        trigger = fixture.nativeElement.querySelector('[data-testid="trigger"]');
        trigger.focus();
        fixture.detectChanges();
    });

    afterEach(() => {
        fixture.destroy();
        fixture.nativeElement.remove();
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    it('enters the panel at the control that the browser visits first, not at the first one in the document', () => {
        const event = tab(trigger);

        expect(event.defaultPrevented).toBe(true);
        expect(document.activeElement).toBe(inPanel('earlier'));
    });

    it('walks the panel in the order of the tabindex values, which the browser would leave the panel for', () => {
        inPanel('earlier').focus();

        const first = tab(inPanel('earlier'));
        expect(first.defaultPrevented).toBe(true);
        expect(document.activeElement).toBe(inPanel('later'));

        const second = tab(inPanel('later'));
        expect(second.defaultPrevented).toBe(true);
        expect(document.activeElement).toBe(inPanel('unprioritized'));
        expect(trigger.getAttribute('aria-expanded')).toBe('true');
    });

    it('walks the panel backwards in the same order on Shift+Tab', () => {
        inPanel('unprioritized').focus();

        const event = tab(inPanel('unprioritized'), true);

        expect(event.defaultPrevented).toBe(true);
        expect(document.activeElement).toBe(inPanel('later'));
    });

    it('leaves the panel on Tab from the last control of the order, which has no positive tabindex', () => {
        inPanel('unprioritized').focus();

        tab(inPanel('unprioritized'));
        fixture.detectChanges();

        expect(document.activeElement).toBe(trigger);
        expect(trigger.getAttribute('aria-expanded')).toBe('false');
    });

    it('returns to the trigger on Shift+Tab from the control that the browser visits first', () => {
        inPanel('earlier').focus();

        const event = tab(inPanel('earlier'), true);
        fixture.detectChanges();

        expect(event.defaultPrevented).toBe(true);
        expect(document.activeElement).toBe(trigger);
    });
});

@Component({
    template: `
        <tumaet-ui-popover #pop openOn="hover" ariaLabel="Theme options" (openChange)="open = $event">
            <button data-testid="inside">Inside</button>
        </tumaet-ui-popover>
        @if (showTrigger()) {
            <button [tumAetUiPopoverTrigger]="pop" data-testid="trigger">Theme</button>
        }
    `,
    imports: [TumAetUiPopoverTriggerDirective, TumAetUiPopoverComponent],
})
class RemovableTriggerHostComponent {
    readonly showTrigger = signal(true);
    open = false;
}

describe('TumAetUiPopoverTriggerDirective whose trigger is removed', () => {
    let fixture: ComponentFixture<RemovableTriggerHostComponent>;
    let host: RemovableTriggerHostComponent;

    beforeEach(async () => {
        vi.useFakeTimers();
        await TestBed.configureTestingModule({ imports: [RemovableTriggerHostComponent] }).compileComponents();
        fixture = TestBed.createComponent(RemovableTriggerHostComponent);
        host = fixture.componentInstance;
        document.body.appendChild(fixture.nativeElement);
        fixture.detectChanges();
        fixture.nativeElement.querySelector('[data-testid="trigger"]').dispatchEvent(new PointerEvent('pointerenter', { pointerType: 'mouse' }));
        fixture.detectChanges();
    });

    afterEach(() => {
        fixture.destroy();
        fixture.nativeElement.remove();
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    function removeTrigger(): void {
        host.showTrigger.set(false);
        fixture.detectChanges();
    }

    it('closes the popover although no pointer leave is reported for the removed trigger', () => {
        expect(host.open).toBe(true);

        removeTrigger();
        vi.advanceTimersByTime(300);

        expect(host.open).toBe(false);
    });

    it('keeps the popover while the focus is in its panel', () => {
        document.querySelector<HTMLElement>('[data-testid="inside"]')!.focus();

        removeTrigger();
        vi.advanceTimersByTime(300);

        expect(host.open).toBe(true);
    });

    it('does not arm a close on a popover that is already destroyed', () => {
        const popover = fixture.debugElement.query(By.directive(TumAetUiPopoverComponent)).componentInstance as TumAetUiPopoverComponent;
        const closeSpy = vi.spyOn(popover, 'close');

        fixture.destroy();
        // What a trigger does that is torn down after its popover.
        popover.triggerRemoved();
        vi.advanceTimersByTime(300);

        expect(closeSpy).not.toHaveBeenCalled();
    });
});
