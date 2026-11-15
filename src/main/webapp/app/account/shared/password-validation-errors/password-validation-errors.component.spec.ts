import { TestBed } from '@angular/core/testing';
import { FormControl, Validators } from '@angular/forms';
import { beforeEach, describe, expect, it } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { PasswordValidationErrorsComponent } from './password-validation-errors.component';

describe('password validation errors', () => {
    beforeEach(() => TestBed.configureTestingModule({ providers: [{ provide: TranslateService, useClass: MockTranslateService }] }));
    it('shows the current control errors with the field-specific translation keys', () => {
        const fixture = TestBed.createComponent(PasswordValidationErrorsComponent);
        const control = new FormControl('', { nonNullable: true, validators: [Validators.required] });
        fixture.componentRef.setInput('control', control);
        fixture.componentRef.setInput('field', 'newpassword');
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('small')?.textContent).toContain('global.messages.validate.newpassword.required');
        control.setValue('valid');
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('small')).toBeNull();
        control.setErrors({ minlength: true });
        fixture.componentRef.setInput('field', 'confirmpassword');
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('small')?.textContent).toContain('global.messages.validate.confirmpassword.minlength');
    });
});
