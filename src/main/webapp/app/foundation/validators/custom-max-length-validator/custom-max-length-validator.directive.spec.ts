import { WritableSignal, inputBinding, signal } from '@angular/core';
import { DirectiveFixture, TestBed } from '@angular/core/testing';
import { FormControl } from '@angular/forms';
import { beforeEach, describe, expect, it } from 'vitest';

import { CustomMaxLengthDirective } from './custom-max-length-validator.directive';

describe('CustomMaxLengthDirective', () => {
    let fixture: DirectiveFixture<CustomMaxLengthDirective>;
    let maxLength: WritableSignal<number>;

    function getDirective(): CustomMaxLengthDirective {
        return fixture.directiveInstance;
    }

    beforeEach(() => {
        maxLength = signal(10);
        fixture = TestBed.createDirective(CustomMaxLengthDirective, {
            tagName: 'input',
            bindings: [inputBinding('customMaxLength', maxLength)],
        });
    });

    it('should return null if the input length is within the limit', () => {
        maxLength.set(10);
        fixture.detectChanges();

        const directive = getDirective();
        const control = new FormControl('12345');
        const result = directive.validate(control);
        expect(result).toBeNull();
    });

    it('should return an error object if the input length exceeds the limit', () => {
        maxLength.set(5);
        fixture.detectChanges();

        const directive = getDirective();
        const control = new FormControl('123456');
        const result = directive.validate(control);
        expect(result).toEqual({ customMaxLength: true });
    });

    it('should return null if the input is null', () => {
        maxLength.set(5);
        fixture.detectChanges();

        const directive = getDirective();
        const control = new FormControl(null);
        const result = directive.validate(control);
        expect(result).toBeNull();
    });

    it('should return null if the input is undefined', () => {
        maxLength.set(5);
        fixture.detectChanges();

        const directive = getDirective();
        const control = new FormControl(undefined);
        const result = directive.validate(control);
        expect(result).toBeNull();
    });

    it('should return null if the input is an empty string', () => {
        maxLength.set(5);
        fixture.detectChanges();

        const directive = getDirective();
        const control = new FormControl('');
        const result = directive.validate(control);
        expect(result).toBeNull();
    });
});
