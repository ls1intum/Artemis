import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { By } from '@angular/platform-browser';
import { beforeEach, describe, expect, it } from 'vitest';
import { CustomNoScientificNotationValidatorDirective } from './custom-no-scientific-notation-validator.directive';

@Component({
    standalone: true,
    template:
        '<form name="editForm" #editForm="ngForm">' +
        '<input type="number" name="bonusPoints" noScientificNotation #bonusPointsModel="ngModel" [(ngModel)]="bonusPoints"/>' +
        '</form>',
    imports: [FormsModule, CustomNoScientificNotationValidatorDirective],
})
class NoScientificNotationComponent {
    bonusPoints?: number;
}

describe('CustomNoScientificNotationValidatorDirective', () => {
    let fixture: ComponentFixture<NoScientificNotationComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CommonModule, FormsModule],
        }).compileComponents();
        fixture = TestBed.createComponent(NoScientificNotationComponent);

        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    });

    const typeIntoInput = async (value: string) => {
        const input = fixture.debugElement.query(By.css('input[name=bonusPoints]'));
        input.nativeElement.value = value;
        input.nativeElement.dispatchEvent(new Event('input'));

        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        return input.references['bonusPointsModel'];
    };

    it.each(['0', '0.1', '0.111', '0.001', '10.5', '9999'])('should accept the decimal value %s', async (value) => {
        const model = await typeIntoInput(value);

        expect(model.errors).toBeNull();
    });

    it.each(['1e-3', '1e-30', '1e3', '1E3'])('should set an error for the scientific notation value %s', async (value) => {
        const model = await typeIntoInput(value);

        expect(model.errors.scientificNotation).toBe(true);
    });

    it('should clear the error once the value is rewritten without scientific notation', async () => {
        expect((await typeIntoInput('1e-3')).errors.scientificNotation).toBe(true);

        const model = await typeIntoInput('0.001');

        expect(model.errors).toBeNull();
    });
});
