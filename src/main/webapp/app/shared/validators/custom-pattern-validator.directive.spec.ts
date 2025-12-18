import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { By } from '@angular/platform-browser';
import { CustomPatternValidatorDirective } from './custom-pattern-validator.directive';

@Component({
    standalone: true,
    template: '<form name="editForm" #editForm="ngForm">' + '<input type="text" name="pattern" validPattern #patternModel="ngModel" [(ngModel)]="pattern"/>' + '</form>',
    imports: [FormsModule, CustomPatternValidatorDirective],
})
class CustomPatternComponent {
    pattern: string;
}

describe('CustomPatternValidatorDirective', () => {
    let fixture: ComponentFixture<CustomPatternComponent>;
    let component = new CustomPatternComponent();

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CommonModule, FormsModule],
        }).compileComponents();
        fixture = TestBed.createComponent(CustomPatternComponent);
        component = fixture.componentInstance;
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
