import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { TumUiPopoverComponent } from './tum-ui-popover.component';

describe('TumUiPopoverComponent', () => {
    let component: TumUiPopoverComponent;
    let fixture: ComponentFixture<TumUiPopoverComponent>;
    let origin: HTMLButtonElement;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [TumUiPopoverComponent] }).compileComponents();
        fixture = TestBed.createComponent(TumUiPopoverComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('ariaLabel', 'Test popover');
        fixture.detectChanges();
        origin = document.createElement('button');
        document.body.appendChild(origin);
    });

    afterEach(() => {
        component.close();
        origin.remove();
        vi.restoreAllMocks();
    });

    it('opens: sets isOpen and emits openChange(true)', () => {
        const emitSpy = vi.spyOn(component.openChange, 'emit');
        component.open(origin);
        expect(component.isOpen()).toBe(true);
        expect(emitSpy).toHaveBeenCalledWith(true);
    });

    it('closes: clears isOpen and emits openChange(false)', () => {
        component.open(origin);
        const emitSpy = vi.spyOn(component.openChange, 'emit');
        component.close();
        expect(component.isOpen()).toBe(false);
        expect(emitSpy).toHaveBeenCalledWith(false);
    });

    it('toggle flips the open state', () => {
        component.toggle(origin);
        expect(component.isOpen()).toBe(true);
        component.toggle(origin);
        expect(component.isOpen()).toBe(false);
    });

    it('does not emit again when opening an already-open popover', () => {
        component.open(origin);
        const emitSpy = vi.spyOn(component.openChange, 'emit');
        component.open(origin);
        expect(emitSpy).not.toHaveBeenCalled();
    });

    it('exposes the open panel as a modal dialog', () => {
        component.open(origin);
        fixture.detectChanges();
        const panel = document.querySelector('.tum-ui-popover-panel');
        expect(panel).not.toBeNull();
        expect(panel?.getAttribute('role')).toBe('dialog');
        expect(panel?.getAttribute('aria-modal')).toBe('true');
    });

    it('renders a pointer-capturing backdrop and closes on backdrop click', () => {
        component.open(origin);
        fixture.detectChanges();
        // The backdrop comes from the shared overlay substrate, which uses CDK's transparent backdrop. The
        // package ships `@angular/cdk/overlay-prebuilt.css`, so it is a full-viewport, pointer-capturing
        // surface — without it the click below would never reach the browser. Assert the backdrop exists and
        // that backdropClick is wired to close.
        const backdrop = document.querySelector('.cdk-overlay-transparent-backdrop');
        expect(backdrop).not.toBeNull();
        backdrop!.dispatchEvent(new MouseEvent('click', { bubbles: true }));
        expect(component.isOpen()).toBe(false);
    });
});
