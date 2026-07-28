import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { vi } from 'vitest';
import { TumUiChipComponent } from './tum-ui-chip.component';

describe('TumUiChipComponent', () => {
    let component: TumUiChipComponent;
    let fixture: ComponentFixture<TumUiChipComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [TumUiChipComponent, FontAwesomeTestingModule] }).compileComponents();
        fixture = TestBed.createComponent(TumUiChipComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    afterEach(() => {
        fixture.destroy();
        vi.restoreAllMocks();
    });

    function label(): HTMLElement {
        return fixture.debugElement.query(By.css('.tum-ui-chip-label')).nativeElement;
    }
    function removeButton(): HTMLButtonElement | null {
        const el = fixture.debugElement.query(By.css('button'));
        return el ? (el.nativeElement as HTMLButtonElement) : null;
    }

    it('renders the label', () => {
        fixture.componentRef.setInput('label', 'Alpha Group');
        fixture.detectChanges();
        expect(label().textContent?.trim()).toBe('Alpha Group');
    });

    it('does not render a remove button by default', () => {
        fixture.componentRef.setInput('label', 'Alpha');
        fixture.detectChanges();
        expect(removeButton()).toBeNull();
    });

    it('renders a labeled remove button when removable and emits onRemove on click', () => {
        fixture.componentRef.setInput('label', 'Alpha');
        fixture.componentRef.setInput('removable', true);
        fixture.componentRef.setInput('removeAriaLabel', 'Remove group');
        fixture.detectChanges();
        const button = removeButton()!;
        expect(button).not.toBeNull();
        expect(button.getAttribute('aria-label')).toBe('Remove group');

        const emitSpy = vi.spyOn(component.onRemove, 'emit');
        button.click();
        expect(emitSpy).toHaveBeenCalledTimes(1);
    });

    it('emits onRemove when Backspace is pressed on the focused remove button', () => {
        fixture.componentRef.setInput('label', 'Alpha');
        fixture.componentRef.setInput('removable', true);
        fixture.detectChanges();
        const emitSpy = vi.spyOn(component.onRemove, 'emit');
        removeButton()!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Backspace', bubbles: true }));
        expect(emitSpy).toHaveBeenCalledTimes(1);
    });

    it('does not emit on unrelated keys', () => {
        fixture.componentRef.setInput('label', 'Alpha');
        fixture.componentRef.setInput('removable', true);
        fixture.detectChanges();
        const emitSpy = vi.spyOn(component.onRemove, 'emit');
        removeButton()!.dispatchEvent(new KeyboardEvent('keydown', { key: 'a', bubbles: true }));
        expect(emitSpy).not.toHaveBeenCalled();
    });
});

@Component({
    template: `<tum-ui-chip><span class="projected">Custom</span></tum-ui-chip>`,
    imports: [TumUiChipComponent],
})
class ChipHostComponent {}

describe('TumUiChipComponent (content projection)', () => {
    it('projects content when no label input is set', async () => {
        await TestBed.configureTestingModule({ imports: [ChipHostComponent, FontAwesomeTestingModule] }).compileComponents();
        const fixture = TestBed.createComponent(ChipHostComponent);
        fixture.detectChanges();
        const projected = fixture.debugElement.query(By.css('.projected'));
        expect(projected).not.toBeNull();
        expect(projected.nativeElement.textContent.trim()).toBe('Custom');
        fixture.destroy();
    });
});
