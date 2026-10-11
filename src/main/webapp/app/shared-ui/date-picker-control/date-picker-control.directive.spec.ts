import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { TumAetUiDatePickerComponent } from '@tumaet/ui-angular';
import dayjs from 'dayjs/esm';
import { DatePickerControlDirective } from 'app/shared-ui/date-picker-control/date-picker-control.directive';

@Component({
    imports: [ReactiveFormsModule, TumAetUiDatePickerComponent, DatePickerControlDirective],
    template: `
        <form [formGroup]="form">
            <tumaet-ui-date-picker formControlName="date" ariaLabel="Date" />
        </form>
    `,
})
class HostComponent {
    readonly form = new FormGroup({ date: new FormControl<dayjs.Dayjs | undefined>(undefined, { nonNullable: true, validators: [Validators.required] }) });
}

describe('DatePickerControlDirective', () => {
    let fixture: ComponentFixture<HostComponent>;
    let host: HostComponent;

    const input = () => fixture.debugElement.query(By.css('input[type="text"]')).nativeElement as HTMLInputElement;
    const type = (text: string) => {
        input().value = text;
        input().dispatchEvent(new Event('input'));
        fixture.detectChanges();
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [HostComponent, FontAwesomeTestingModule] }).compileComponents();
        fixture = TestBed.createComponent(HostComponent);
        host = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('shows a value set on the control, whether it is a dayjs or a Date', () => {
        host.form.controls.date.setValue(dayjs('2026-06-13T08:30'));
        fixture.detectChanges();
        expect(input().value).toBe('13.06.2026 08:30');

        host.form.controls.date.setValue(new Date('2026-07-01T10:15') as unknown as dayjs.Dayjs);
        fixture.detectChanges();
        expect(input().value).toBe('01.07.2026 10:15');
    });

    it('writes a typed date to the control as a dayjs and marks it dirty', () => {
        type('13.06.2026 08:30');
        const value = host.form.controls.date.value;
        expect(dayjs.isDayjs(value)).toBe(true);
        expect(value?.format('YYYY-MM-DD HH:mm')).toBe('2026-06-13 08:30');
        expect(host.form.controls.date.dirty).toBe(true);
    });

    it('does not treat a value written by the form as a change of the user', () => {
        host.form.controls.date.setValue(dayjs('2026-06-13T08:30'));
        fixture.detectChanges();
        expect(host.form.controls.date.dirty).toBe(false);
    });

    it('reports text that is not a date as an invalidDate error and clears it once the text is a date', () => {
        type('13.06.');
        expect(host.form.controls.date.errors).toEqual(expect.objectContaining({ invalidDate: true }));

        type('13.06.2026 08:30');
        expect(host.form.controls.date.errors).toBeNull();
    });

    it('keeps the required validator of the control working for an empty field', () => {
        expect(host.form.controls.date.errors).toEqual({ required: true });
    });

    it('marks the control touched when the field loses focus', () => {
        input().dispatchEvent(new Event('blur'));
        expect(host.form.controls.date.touched).toBe(true);
    });
});
