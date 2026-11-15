import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TumAetUiButtonSeverity } from '@tumaet/ui-angular';
import { ExerciseActionButtonComponent } from './exercise-action-button.component';

// The component's attribute selector must be composed on a real native button to exercise disabled clicks.
@Component({
    imports: [ExerciseActionButtonComponent],
    template: `<button jhi-exercise-action-button buttonLabel="Export" [severity]="severity()" [overrideSecondary]="secondary()" [buttonLoading]="loading()"></button>`,
})
class ButtonHostComponent {
    readonly severity = signal<TumAetUiButtonSeverity | undefined>(undefined);
    readonly secondary = signal<boolean | undefined>(undefined);
    readonly loading = signal(false);
}

describe('ExerciseActionButtonComponent', () => {
    let fixture: ComponentFixture<ButtonHostComponent>;
    let button: HTMLButtonElement;

    beforeEach(() => {
        fixture = TestBed.createComponent(ButtonHostComponent);
        fixture.detectChanges();
        button = fixture.nativeElement.querySelector('button');
    });

    it('keeps explicit action severity when the legacy secondary override changes', () => {
        fixture.componentInstance.severity.set('warn');
        fixture.componentInstance.secondary.set(true);
        fixture.detectChanges();
        expect(button.classList.contains('tumaet-ui-btn')).toBe(true);
        expect(button.className).toContain('button-warn');
        expect(button.classList.contains('btn')).toBe(false);
    });

    it('preserves the legacy secondary toggle when no action severity is supplied', () => {
        fixture.componentInstance.secondary.set(true);
        fixture.detectChanges();
        expect(button.className).toContain('button-secondary');
        fixture.componentInstance.secondary.set(false);
        fixture.detectChanges();
        expect(button.className).toContain('button-primary');
    });

    it('blocks native clicks while loading and restores the enabled button afterwards', () => {
        let clicks = 0;
        button.addEventListener('click', () => clicks++);
        fixture.componentInstance.loading.set(true);
        fixture.detectChanges();
        expect(button.disabled).toBe(true);
        expect(button.getAttribute('aria-busy')).toBe('true');
        button.click();
        expect(clicks).toBe(0);
        fixture.componentInstance.loading.set(false);
        fixture.detectChanges();
        expect(button.disabled).toBe(false);
        button.click();
        expect(clicks).toBe(1);
    });
});
