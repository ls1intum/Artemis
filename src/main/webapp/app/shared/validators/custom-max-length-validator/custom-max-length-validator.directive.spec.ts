import { CustomMaxLengthDirective } from './custom-max-length-validator.directive';
import { FormControl } from '@angular/forms';

describe('CustomMaxLengthDirective', () => {
    let directive: CustomMaxLengthDirective;

    beforeEach(() => {
        directive = new CustomMaxLengthDirective();
    });

    it('should return null if the input length is within the limit', () => {
        directive.customMaxLength = 10;
        const control = new FormControl('12345');
        const result = directive.validate(control);
        expect(result).toBeNull();
    });

    it('should return an error object if the input length exceeds the limit', () => {
        directive.customMaxLength = 5;
        const control = new FormControl('123456');
        const result = directive.validate(control);
        expect(result).toEqual({ customMaxLength: true });
    });

    it('should return null if the input is null', () => {
        directive.customMaxLength = 5;
        const control = new FormControl(null);
        const result = directive.validate(control);
        expect(result).toBeNull();
    });

    it('should return null if the input is undefined', () => {
        directive.customMaxLength = 5;
        const control = new FormControl(undefined);
        const result = directive.validate(control);
        expect(result).toBeNull();
    });

    it('should return null if the input is an empty string', () => {
        directive.customMaxLength = 5;
        const control = new FormControl('');
        const result = directive.validate(control);
        expect(result).toBeNull();
    });
});
