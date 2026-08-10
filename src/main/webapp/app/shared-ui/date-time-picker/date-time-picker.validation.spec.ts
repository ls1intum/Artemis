import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import dayjs from 'dayjs/esm';
import { DateTimePickerType, FormDateTimePickerComponent } from 'app/shared-ui/date-time-picker/date-time-picker.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

// Mimics the competency form: an optional date bound with formControlName, and a submit button gated on
// form validity. Typing nonsense used to leave the control valid, so the form submitted and dropped the
// entry silently while the picker showed its inline error (#13443 review — Pomodorka3).
@Component({
    template: `
        <form [formGroup]="form">
            <jhi-date-time-picker formControlName="softDueDate" [pickerType]="CALENDAR" [shouldDisplayTimeZoneWarning]="false" />
        </form>
    `,
    imports: [ReactiveFormsModule, FormDateTimePickerComponent],
})
class HostComponent {
    form = new FormGroup({ softDueDate: new FormControl<dayjs.Dayjs | undefined>(undefined) });
    readonly CALENDAR = DateTimePickerType.CALENDAR;
}

describe('date-time-picker reports unparseable input to its form control', () => {
    let fixture: ComponentFixture<HostComponent>;
    let host: HostComponent;
    let picker: FormDateTimePickerComponent;

    const date = new Date('2026-06-03T00:00:00');

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [HostComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
        fixture = TestBed.createComponent(HostComponent);
        host = fixture.componentInstance;
        fixture.detectChanges();
        picker = fixture.debugElement.query((de) => de.componentInstance instanceof FormDateTimePickerComponent).componentInstance;
    });

    it('leaves an untouched optional field valid', () => {
        expect(host.form.valid).toBe(true);
    });

    it('marks the form invalid while the field holds unparseable text', () => {
        picker.updateField('asdasd');
        fixture.detectChanges();

        expect(host.form.controls.softDueDate.errors).toEqual({ invalidDate: true });
        expect(host.form.valid).toBe(false);
    });

    it('marks the form invalid when a valid prefix is followed by junk (rejected on blur)', () => {
        picker.updateField(date);
        picker.onInputBlur({ target: { value: '03.06.2026abc' } } as unknown as Event);
        fixture.detectChanges();

        expect(host.form.valid).toBe(false);
    });

    it('recovers once the user corrects the input', () => {
        picker.updateField('asdasd');
        fixture.detectChanges();
        expect(host.form.valid).toBe(false);

        picker.updateField(date);
        fixture.detectChanges();

        expect(host.form.valid).toBe(true);
        expect(host.form.controls.softDueDate.value?.toDate()).toEqual(date);
    });

    it('recovers when the field is cleared', () => {
        picker.updateField('asdasd');
        fixture.detectChanges();
        expect(host.form.valid).toBe(false);

        picker.updateField(null);
        fixture.detectChanges();

        expect(host.form.valid).toBe(true);
        expect(host.form.controls.softDueDate.value).toBeUndefined();
    });

    it('recovers when the parent resets the control', () => {
        picker.updateField('asdasd');
        fixture.detectChanges();
        expect(host.form.valid).toBe(false);

        host.form.reset();
        fixture.detectChanges();

        expect(host.form.valid).toBe(true);
    });
});
