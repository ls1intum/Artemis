import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { By } from '@angular/platform-browser';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { beforeEach, describe, expect, it } from 'vitest';
import { CustomScientificNotationValidatorDirective } from './custom-scientific-notation-validator.directive';

@Component({
    standalone: true,
    template: '<form name="editForm" #editForm="ngForm">' + '<input type="number" name="points" noScientificNotation #pointsModel="ngModel" [(ngModel)]="points"/>' + '</form>',
    imports: [FormsModule, CustomScientificNotationValidatorDirective],
})
class CustomScientificNotationComponent {
    points!: number;
}

describe('CustomScientificNotationValidatorDirective', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<CustomScientificNotationComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CommonModule, FormsModule],
        }).compileComponents();
        fixture = TestBed.createComponent(CustomScientificNotationComponent);
        // Required before simulating any typing: this is what wires NgModel's ControlValueAccessor
        // (registerOnChange) to the native input. Before this runs once, NumberValueAccessor's
        // onChange is still a no-op stub, so a dispatched 'input' event would silently go nowhere.
        fixture.detectChanges();
    });

    /**
     * Simulates a user typing `rawValue` into the field: sets the native input's raw string value
     * directly and dispatches a real 'input' event, exactly like a keystroke would. This is
     * important because assigning a JS number to the bound ngModel property and letting Angular's
     * NumberValueAccessor write it back to the DOM re-serializes it via `String(value)`, which
     * collapses e.g. `1e2` into `"100"` - losing the scientific-notation format we're testing for.
     */
    function typeIntoInput(rawValue: string) {
        const inputElement: HTMLInputElement = fixture.debugElement.query(By.css('input[name=points]')).nativeElement;
        inputElement.value = rawValue;
        inputElement.dispatchEvent(new Event('input', { bubbles: true }));
        fixture.detectChanges();

        return fixture.debugElement.query(By.css('input[name=points]')).references['pointsModel'];
    }

    it('should accept plain decimal values', () => {
        const pointsEl = typeIntoInput('1.5');

        expect(pointsEl.errors).toBeNull();
    });

    it('should set error on negative-exponent scientific notation values (issue #12451)', () => {
        const pointsEl = typeIntoInput('1e-30');

        expect(pointsEl.errors.scientificNotation).toBe(true);
    });

    it('should set error on positive-exponent scientific notation values', () => {
        const pointsEl = typeIntoInput('1e2');

        expect(pointsEl.errors.scientificNotation).toBe(true);
    });

    it('should accept an empty value', () => {
        const pointsEl = typeIntoInput('');

        expect(pointsEl.errors).toBeNull();
    });
});
