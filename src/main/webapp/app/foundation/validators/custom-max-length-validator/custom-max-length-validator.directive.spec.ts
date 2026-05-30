import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, FormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';

import { CustomMaxLengthDirective } from './custom-max-length-validator.directive';

@Component({
    template: `<input [customMaxLength]="maxLength" ngModel />`,
    standalone: true,
    imports: [CustomMaxLengthDirective, FormsModule],
})
class TestHostComponent {
    maxLength = 10;
}

describe('CustomMaxLengthDirective', () => {
    let fixture: ComponentFixture<TestHostComponent>;
    let host: TestHostComponent;

    function getDirective(): CustomMaxLengthDirective {
        const inputDe = fixture.debugElement.query(By.css('input'));
        return inputDe.injector.get(CustomMaxLengthDirective);
    }

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TestHostComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(TestHostComponent);
        host = fixture.componentInstance;
    });

    it('should return null if the input length is within the limit', () => {
        host.maxLength = 10;
        fixture.detectChanges();

        const directive = getDirective();
        const control = new FormControl('12345');
        const result = directive.validate(control);
        expect(result).toBeNull();
    });

    it('should return an error object if the input length exceeds the limit', () => {
        host.maxLength = 5;
        fixture.detectChanges();

        const directive = getDirective();
        const control = new FormControl('123456');
        const result = directive.validate(control);
        expect(result).toEqual({ customMaxLength: true });
    });

    it('should return null if the input is null', () => {
        host.maxLength = 5;
        fixture.detectChanges();

        const directive = getDirective();
        const control = new FormControl(null);
        const result = directive.validate(control);
        expect(result).toBeNull();
    });

    it('should return null if the input is undefined', () => {
        host.maxLength = 5;
        fixture.detectChanges();

        const directive = getDirective();
        const control = new FormControl(undefined);
        const result = directive.validate(control);
        expect(result).toBeNull();
    });

    it('should return null if the input is an empty string', () => {
        host.maxLength = 5;
        fixture.detectChanges();

        const directive = getDirective();
        const control = new FormControl('');
        const result = directive.validate(control);
        expect(result).toBeNull();
    });
});
