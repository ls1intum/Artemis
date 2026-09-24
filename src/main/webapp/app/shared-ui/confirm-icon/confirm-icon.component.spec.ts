import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ConfirmIconComponent } from 'app/shared-ui/confirm-icon/confirm-icon.component';

describe('ConfirmIconComponent', () => {
    let component: ConfirmIconComponent;
    let fixture: ComponentFixture<ConfirmIconComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({ imports: [ConfirmIconComponent] });
        fixture = TestBed.createComponent(ConfirmIconComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('initialTooltip', 'Delete');
        fixture.componentRef.setInput('confirmTooltip', 'Confirm delete');
        fixture.detectChanges();
    });

    it('should keep the same button element and focus across both activations, then emit confirmEvent', () => {
        const confirmSpy = vi.fn();
        component.confirmEvent.subscribe(confirmSpy);

        const button: HTMLButtonElement = fixture.nativeElement.querySelector('button');
        button.focus();
        expect(document.activeElement).toBe(button);

        // first activation (Enter/Space triggers a click on a native button) arms the confirmation
        button.click();
        fixture.detectChanges();

        expect(component.showConfirm()).toBe(true);
        expect(fixture.nativeElement.querySelector('button')).toBe(button);
        expect(document.activeElement).toBe(button);
        expect(confirmSpy).not.toHaveBeenCalled();

        // second activation on the very same element completes the two-step confirmation
        button.click();
        fixture.detectChanges();

        expect(confirmSpy).toHaveBeenCalledWith(true);
        expect(component.showConfirm()).toBe(false);
    });

    it('should revert to the initial state without emitting when focus moves away while confirming', () => {
        const confirmSpy = vi.fn();
        component.confirmEvent.subscribe(confirmSpy);

        const button: HTMLButtonElement = fixture.nativeElement.querySelector('button');
        button.click();
        fixture.detectChanges();
        expect(component.showConfirm()).toBe(true);

        button.dispatchEvent(new FocusEvent('blur'));
        fixture.detectChanges();

        expect(component.showConfirm()).toBe(false);
        expect(confirmSpy).not.toHaveBeenCalled();
    });
});
