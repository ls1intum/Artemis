import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { CustomPatternValidatorDirective } from 'app/shared/validators/custom-pattern-validator.directive';
import { ArtemisTestModule } from '../test.module';

@Component({
    template: '<form name="editForm" #editForm="ngForm">' + '<input type="text" name="pattern" validPattern #patternModel="ngModel" [(ngModel)]="pattern"/>' + '</form>',
})
class CustomPatternComponent {
    pattern: string;
}

describe('CustomPatternValidatorDirective', () => {
    let fixture: ComponentFixture<CustomPatternComponent>;
    let component = new CustomPatternComponent();

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, CommonModule, FormsModule],
            declarations: [CustomPatternValidatorDirective, CustomPatternComponent],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CustomPatternComponent);
                component = fixture.componentInstance;
            });
    });

    it('should accept correct patterns', fakeAsync(() => {
        component.pattern = '^.*@example.com$';

        fixture.detectChanges();

        fixture.whenStable().then(() => {
            fixture.detectChanges();

            const patternEl = fixture.debugElement.query(By.css('input[name=pattern]')).references['patternModel'];

            expect(patternEl.errors).toBeNull();
        });
    }));

    it('should set error on incorrect patterns', fakeAsync(() => {
        component.pattern = '*@example.com$';

        fixture.detectChanges();

        fixture.whenStable().then(() => {
            fixture.detectChanges();

            const patternEl = fixture.debugElement.query(By.css('input[name=pattern]')).references['patternModel'];

            expect(patternEl.errors.validPattern).toBeTrue();
        });
    }));
});
