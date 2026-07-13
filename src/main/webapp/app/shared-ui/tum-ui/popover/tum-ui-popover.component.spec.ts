import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { vi } from 'vitest';
import { TumUiPopoverComponent } from 'app/shared-ui/tum-ui/popover/tum-ui-popover.component';

describe('TumUiPopoverComponent', () => {
    setupTestBed({ zoneless: true });

    let component: TumUiPopoverComponent;
    let fixture: ComponentFixture<TumUiPopoverComponent>;
    let origin: HTMLButtonElement;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [TumUiPopoverComponent] }).compileComponents();
        fixture = TestBed.createComponent(TumUiPopoverComponent);
        component = fixture.componentInstance;
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
});
