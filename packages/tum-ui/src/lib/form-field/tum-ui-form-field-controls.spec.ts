import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { TumUiFormFieldComponent } from './tum-ui-form-field.component';
import { TumUiSelectComponent } from '../select/tum-ui-select.component';
import { TumUiInputNumberComponent } from '../input-number/tum-ui-input-number.component';

/**
 * The package controls that own their inner focusable element have to adopt an enclosing form field the same
 * way a bare `<input tumUiInput>` does, or the field's label would point at nothing.
 */
@Component({
    imports: [TumUiFormFieldComponent, TumUiSelectComponent, TumUiInputNumberComponent],
    template: `
        <tum-ui-form-field label="Language" [hint]="hint()" [invalid]="invalid()" error="Pick a language">
            <tum-ui-select [options]="['English', 'German']" [inputId]="selectId()" />
        </tum-ui-form-field>
        <tum-ui-form-field label="Points" [hint]="hint()" [invalid]="invalid()" error="Points are required">
            <tum-ui-input-number />
        </tum-ui-form-field>
    `,
})
class HostComponent {
    readonly hint = signal<string | undefined>(undefined);
    readonly invalid = signal(false);
    readonly selectId = signal<string | undefined>(undefined);
}

describe('tum-ui-form-field with package controls', () => {
    let fixture: ComponentFixture<HostComponent>;
    let host: HostComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [HostComponent, FontAwesomeTestingModule] }).compileComponents();
        fixture = TestBed.createComponent(HostComponent);
        host = fixture.componentInstance;
        fixture.detectChanges();
    });

    const labels = () => fixture.debugElement.queryAll(By.css('label')).map((el) => el.nativeElement as HTMLLabelElement);
    const selectTrigger = () => fixture.debugElement.query(By.css('tum-ui-select button[role="combobox"]')).nativeElement as HTMLElement;
    const numberInput = () => fixture.debugElement.query(By.css('tum-ui-input-number input')).nativeElement as HTMLInputElement;

    it('labels a select by the id its trigger adopts from the field', () => {
        expect(selectTrigger().id).toBeTruthy();
        expect(labels()[0].getAttribute('for')).toBe(selectTrigger().id);
    });

    it('labels an input number by the id its inner input adopts from the field', () => {
        expect(numberInput().id).toBeTruthy();
        expect(labels()[1].getAttribute('for')).toBe(numberInput().id);
    });

    it('labels the id a select brought with it rather than the generated one', () => {
        host.selectId.set('language-select');
        fixture.detectChanges();

        expect(selectTrigger().id).toBe('language-select');
        expect(labels()[0].getAttribute('for')).toBe('language-select');
    });

    it('describes a select by the field hint', () => {
        host.hint.set('Used for the interface');
        fixture.detectChanges();

        const hintId = (fixture.debugElement.queryAll(By.css('.tum-ui-form-field-hint'))[0].nativeElement as HTMLElement).id;
        expect(selectTrigger().getAttribute('aria-describedby')).toBe(hintId);
    });

    it('describes an input number by the field error without repeating it', () => {
        host.invalid.set(true);
        fixture.detectChanges();

        const errorId = (fixture.debugElement.queryAll(By.css('.tum-ui-form-field-error'))[1].nativeElement as HTMLElement).id;
        expect(numberInput().getAttribute('aria-describedby')).toBe(errorId);
    });

    it('marks a select invalid from the field', () => {
        expect(selectTrigger().className).not.toContain('tum:border-state-danger');

        host.invalid.set(true);
        fixture.detectChanges();

        expect(selectTrigger().className).toContain('tum:border-state-danger');
    });

    it('marks an input number invalid from the field', () => {
        expect(numberInput().className).not.toContain('tum:border-state-danger');

        host.invalid.set(true);
        fixture.detectChanges();

        expect(numberInput().className).toContain('tum:border-state-danger');
    });

    it('marks the inner input of an input number aria-invalid from the field', () => {
        expect(numberInput().getAttribute('aria-invalid')).toBeNull();

        host.invalid.set(true);
        fixture.detectChanges();

        expect(numberInput().getAttribute('aria-invalid')).toBe('true');
    });
});
