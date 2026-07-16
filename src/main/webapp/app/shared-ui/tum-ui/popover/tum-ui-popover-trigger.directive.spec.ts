import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { TumUiPopoverComponent } from 'app/shared-ui/tum-ui/popover/tum-ui-popover.component';
import { TumUiPopoverTriggerDirective } from 'app/shared-ui/tum-ui/popover/tum-ui-popover-trigger.directive';

@Component({
    template: `
        <button [tumUiPopoverTrigger]="pop" data-testid="trigger">Open</button>
        <tum-ui-popover #pop ariaLabel="Test popover">Panel content</tum-ui-popover>
    `,
    imports: [TumUiPopoverTriggerDirective, TumUiPopoverComponent],
})
class PopoverTriggerHostComponent {}

describe('TumUiPopoverTriggerDirective', () => {
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

    it('toggles the popover closed on a second click', () => {
        button.click();
        fixture.detectChanges();
        button.click();
        fixture.detectChanges();
        expect(button.getAttribute('aria-expanded')).toBe('false');
    });
});
