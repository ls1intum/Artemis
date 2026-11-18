import { beforeEach, describe, expect, it } from 'vitest';
import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { PointsInputComponent } from 'app/shared-ui/tum-aet-ui-integration/points-input/points-input.component';
import { typeIntoInputNumber } from 'src/test/javascript/spec/helpers/input-number-test-helpers';

@Component({
    template: `<jhi-points-input inputId="points" name="points" showButtons [step]="0.5" [snapToStep]="snapToStep()" [min]="min" [max]="max" [formControl]="control" />`,
    imports: [PointsInputComponent, ReactiveFormsModule],
})
class ReactiveHostComponent {
    min: number | undefined = 1;
    max: number | undefined = 100;
    readonly snapToStep = signal(false);
    readonly control = new FormControl<number | undefined>(undefined);
}

@Component({
    template: `<form><jhi-points-input inputId="points" name="points" [(ngModel)]="points" required [min]="1" /></form>`,
    imports: [PointsInputComponent, FormsModule],
})
class TemplateDrivenHostComponent {
    points: number | undefined = 7;
}

describe('PointsInputComponent', () => {
    describe('in a reactive form', () => {
        let fixture: ComponentFixture<ReactiveHostComponent>;
        let host: ReactiveHostComponent;

        const input = () => fixture.nativeElement.querySelector('input#points') as HTMLInputElement;

        const settle = async () => {
            fixture.detectChanges();
            await fixture.whenStable();
            fixture.detectChanges();
        };

        beforeEach(async () => {
            fixture = TestBed.createComponent(ReactiveHostComponent);
            host = fixture.componentInstance;
            await settle();
        });

        it('should show the value of the control and take the id and the name', async () => {
            host.control.setValue(2.5);
            await settle();

            expect(input().value).toBe('2.5');
            expect(input().name).toBe('points');
        });

        it.each([
            ['.', '5.5'],
            [',', '5,5'],
        ])('should take the decimal separator %s while typing', async (_separator, typed) => {
            typeIntoInputNumber(input(), typed);
            await settle();

            expect(host.control.value).toBe(5.5);
            expect(input().value).toBe('5.5');
        });

        it('should cut off a fraction that is longer than three digits', async () => {
            typeIntoInputNumber(input(), '5.55555');
            await settle();

            expect(host.control.value).toBe(5.555);
            expect(input().value).toBe('5.555');
        });

        it('should not group the digits', async () => {
            host.control.setValue(12345.5);
            await settle();

            expect(input().value).toBe('12345.5');
        });

        it('should clear the control when the field is emptied', async () => {
            host.control.setValue(5);
            await settle();

            typeIntoInputNumber(input(), '');
            await settle();

            expect(host.control.value).toBeUndefined();
        });

        it('should report a value below the minimum and above the maximum as an error until the field is left', () => {
            host.control.setValue(0.5);
            expect(host.control.errors).toEqual({ min: { min: 1, actual: 0.5 } });

            host.control.setValue(101);
            expect(host.control.errors).toEqual({ max: { max: 100, actual: 101 } });

            host.control.setValue(50);
            expect(host.control.errors).toBeNull();
        });

        it('should apply the bounds when the field is left', async () => {
            typeIntoInputNumber(input(), '500');
            await settle();

            expect(host.control.value).toBe(100);
            expect(host.control.errors).toBeNull();
        });

        it('should not report an empty value as an error of the bounds', () => {
            host.control.setValue(undefined);

            expect(host.control.errors).toBeNull();
        });

        it('should disable the field with the control', async () => {
            host.control.disable();
            await settle();

            expect(input().disabled).toBe(true);
        });

        it('should mark the control as touched when the field is left', () => {
            expect(host.control.touched).toBe(false);

            input().dispatchEvent(new FocusEvent('focusout', { bubbles: true }));

            expect(host.control.touched).toBe(true);
        });

        it('should add the step to an off-grid value with the arrow key unless snapToStep is set', async () => {
            typeIntoInputNumber(input(), '1.3', { leave: false });
            input().dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp', bubbles: true, cancelable: true }));
            await settle();
            expect(host.control.value).toBe(1.8);

            host.snapToStep.set(true);
            await settle();
            typeIntoInputNumber(input(), '1.3', { leave: false });
            input().dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp', bubbles: true, cancelable: true }));
            await settle();
            expect(host.control.value).toBe(1.5);
            typeIntoInputNumber(input(), '1.3', { leave: false });
            input().dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true, cancelable: true }));
            await settle();
            expect(host.control.value).toBe(1);
        });

        it('should use the same input as every other points input', () => {
            const points = fixture.debugElement.query(By.directive(PointsInputComponent)).componentInstance as PointsInputComponent;

            expect(points).toBeInstanceOf(PointsInputComponent);
            expect(input().getAttribute('inputmode')).toBe('decimal');
        });
    });

    describe('in a template driven form', () => {
        let fixture: ComponentFixture<TemplateDrivenHostComponent>;

        const input = () => fixture.nativeElement.querySelector('input#points') as HTMLInputElement;

        // the model reaches the control and the inner input in two asynchronous steps
        const settle = async () => {
            for (let round = 0; round < 2; round++) {
                fixture.detectChanges();
                await fixture.whenStable();
            }
            fixture.detectChanges();
        };

        beforeEach(async () => {
            fixture = TestBed.createComponent(TemplateDrivenHostComponent);
            await settle();
        });

        it('should show the model and write typed values back to it', async () => {
            expect(input().value).toBe('7');

            typeIntoInputNumber(input(), '12.25');
            await settle();

            expect(fixture.componentInstance.points).toBe(12.25);
        });

        it('should be required', async () => {
            typeIntoInputNumber(input(), '');
            await settle();

            expect(fixture.componentInstance.points).toBeUndefined();
            expect(fixture.nativeElement.querySelector('form').classList).toContain('ng-invalid');
        });
    });
});
