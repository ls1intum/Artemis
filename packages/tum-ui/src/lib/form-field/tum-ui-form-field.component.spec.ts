import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TumUiFormFieldComponent } from './tum-ui-form-field.component';
import { TumUiInputDirective } from '../input/tum-ui-input.directive';

@Component({
    imports: [TumUiFormFieldComponent, TumUiInputDirective],
    template: `
        <tum-ui-form-field [label]="label()" [controlId]="controlId()" [required]="required()" [hint]="hint()" [invalid]="invalid()" [error]="error()">
            <input tumUiInput [tumUiInputId]="explicitId()" />
        </tum-ui-form-field>
    `,
})
class HostComponent {
    readonly label = signal('Login');
    readonly controlId = signal<string | undefined>(undefined);
    readonly required = signal(false);
    readonly hint = signal<string | undefined>(undefined);
    readonly invalid = signal(false);
    readonly error = signal<string | undefined>(undefined);
    readonly explicitId = signal<string | undefined>(undefined);
}

describe('TumUiFormFieldComponent', () => {
    let fixture: ComponentFixture<HostComponent>;
    let host: HostComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
        fixture = TestBed.createComponent(HostComponent);
        host = fixture.componentInstance;
        fixture.detectChanges();
    });

    const label = () => fixture.debugElement.query(By.css('label')).nativeElement as HTMLLabelElement;
    const control = () => fixture.debugElement.query(By.css('input')).nativeElement as HTMLInputElement;
    const hintEl = () => fixture.debugElement.query(By.css('.tum-ui-form-field-hint'))?.nativeElement as HTMLElement | undefined;
    const errorEl = () => fixture.debugElement.query(By.css('.tum-ui-form-field-error'))?.nativeElement as HTMLElement | undefined;

    it('renders the label text', () => {
        expect(label().textContent!.trim()).toBe('Login');
    });

    it('labels the projected control by generating an id it adopts', () => {
        expect(control().id).toBeTruthy();
        expect(label().getAttribute('for')).toBe(control().id);
    });

    it('uses the given controlId for both the label and the projected control', () => {
        host.controlId.set('login');
        fixture.detectChanges();

        expect(label().getAttribute('for')).toBe('login');
        expect(control().id).toBe('login');
    });

    it('leaves a control that already carries its own id untouched, and labels that id', () => {
        host.explicitId.set('my-own-id');
        fixture.detectChanges();

        expect(control().id).toBe('my-own-id');
        expect(label().getAttribute('for')).toBe('my-own-id');
    });

    it('prefers an explicit controlId over the id the control brought', () => {
        host.explicitId.set('my-own-id');
        host.controlId.set('login');
        fixture.detectChanges();

        expect(label().getAttribute('for')).toBe('login');
        expect(control().id).toBe('login');
    });

    it('marks a required field with a marker hidden from assistive technology', () => {
        expect(fixture.debugElement.query(By.css('.tum-ui-form-field-required'))).toBeNull();

        host.required.set(true);
        fixture.detectChanges();

        const marker = fixture.debugElement.query(By.css('.tum-ui-form-field-required')).nativeElement as HTMLElement;
        expect(marker.getAttribute('aria-hidden')).toBe('true');
    });

    it('describes the control by its hint', () => {
        host.hint.set('Used to sign in');
        fixture.detectChanges();

        expect(hintEl()!.textContent!.trim()).toBe('Used to sign in');
        expect(control().getAttribute('aria-describedby')).toBe(hintEl()!.id);
    });

    it('renders no hint element and no description when there is no hint', () => {
        expect(hintEl()).toBeUndefined();
        expect(control().getAttribute('aria-describedby')).toBeNull();
    });

    it('hides the error region and keeps it out of the description while the field is valid', () => {
        host.error.set('Login is required');
        fixture.detectChanges();

        expect(errorEl()!.hidden).toBe(true);
        expect(control().getAttribute('aria-describedby')).toBeNull();
    });

    it('announces the error and describes the control by it when invalid', () => {
        host.error.set('Login is required');
        host.invalid.set(true);
        fixture.detectChanges();

        expect(errorEl()!.hidden).toBe(false);
        expect(errorEl()!.getAttribute('role')).toBe('alert');
        expect(errorEl()!.textContent!.trim()).toBe('Login is required');
        expect(control().getAttribute('aria-describedby')).toBe(errorEl()!.id);
    });

    it('replaces the hint description with the error description when invalid', () => {
        host.hint.set('Used to sign in');
        host.error.set('Login is required');
        host.invalid.set(true);
        fixture.detectChanges();

        expect(control().getAttribute('aria-describedby')).toBe(errorEl()!.id);
    });

    it('propagates the invalid state to the projected control', () => {
        expect(control().className).not.toContain('tum:border-state-danger');

        host.invalid.set(true);
        fixture.detectChanges();

        expect(control().className).toContain('tum:border-state-danger');
    });

    it('marks the projected control aria-invalid only while the field is invalid', () => {
        // The danger border is a visual cue only, so the state has to reach assistive technology as well.
        expect(control().getAttribute('aria-invalid')).toBeNull();

        host.invalid.set(true);
        fixture.detectChanges();

        expect(control().getAttribute('aria-invalid')).toBe('true');

        host.invalid.set(false);
        fixture.detectChanges();

        expect(control().getAttribute('aria-invalid')).toBeNull();
    });
});
