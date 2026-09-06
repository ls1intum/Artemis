import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, signal } from '@angular/core';
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
            <jhi-date-time-picker formControlName="softDueDate" [pickerType]="CALENDAR" [min]="min()" [max]="max()" [shouldDisplayTimeZoneWarning]="false" />
        </form>
    `,
    imports: [ReactiveFormsModule, FormDateTimePickerComponent],
})
class HostComponent {
    form = new FormGroup({ softDueDate: new FormControl<dayjs.Dayjs | undefined>(undefined) });
    min = signal<dayjs.Dayjs | undefined>(undefined);
    max = signal<dayjs.Dayjs | undefined>(undefined);
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

    // Programmatic writes reach the model through writeValue, not updateField, so the range has to be
    // rechecked there as well. Otherwise a parent could patch in a date the user is not allowed to type
    // and the form would happily submit it (CodeRabbit / Claudia-Anthropica on #13472).
    describe('programmatic writes outside [min]/[max]', () => {
        const min = dayjs('2026-06-01T00:00:00');
        const max = dayjs('2026-06-30T00:00:00');

        beforeEach(() => {
            host.min.set(min);
            host.max.set(max);
            fixture.detectChanges();
        });

        it('marks the form invalid when setValue writes a date before [min]', () => {
            host.form.controls.softDueDate.setValue(min.subtract(1, 'day'));
            fixture.detectChanges();

            expect(host.form.controls.softDueDate.errors).toEqual({ invalidDate: true });
            expect(host.form.valid).toBe(false);
        });

        it('marks the form invalid when patchValue writes a date after [max]', () => {
            host.form.patchValue({ softDueDate: max.add(1, 'day') });
            fixture.detectChanges();

            expect(host.form.controls.softDueDate.errors).toEqual({ invalidDate: true });
            expect(host.form.valid).toBe(false);
        });

        it('accepts programmatic values on the bounds, which are inclusive', () => {
            host.form.controls.softDueDate.setValue(min);
            fixture.detectChanges();
            expect(host.form.valid).toBe(true);

            host.form.controls.softDueDate.setValue(max);
            fixture.detectChanges();

            expect(host.form.valid).toBe(true);
        });

        // The bounds routinely bind or move after a value is in the field (an exercise form takes the due
        // date's [min] from the release date the user is still editing), and neither updateField nor
        // updateSignals runs for a bound change on its own.
        it('turns invalid when a bound moves past a value that was valid when written', () => {
            const value = min.add(5, 'day');
            host.form.controls.softDueDate.setValue(value);
            fixture.detectChanges();
            expect(host.form.valid).toBe(true);

            host.min.set(value.add(1, 'day'));
            fixture.detectChanges();

            expect(host.form.controls.softDueDate.errors).toEqual({ invalidDate: true });
            expect(host.form.valid).toBe(false);
        });

        it('turns valid again when the bound moves back off the value', () => {
            const value = min.add(5, 'day');
            host.form.controls.softDueDate.setValue(value);
            host.max.set(value.subtract(1, 'day'));
            fixture.detectChanges();
            expect(host.form.valid).toBe(false);

            host.max.set(value.add(1, 'day'));
            fixture.detectChanges();

            expect(host.form.valid).toBe(true);
        });

        // A date the range rejected is a good date in every other respect, so a bound that moves to include it
        // should accept the entry rather than make the user retype it (Claudia-Anthropica on #13472).
        it('accepts a typed date the range had rejected once a bound moves to include it', () => {
            const typed = max.add(3, 'day');
            picker.updateField(typed.toDate());
            fixture.detectChanges();
            expect(host.form.valid).toBe(false);
            expect(host.form.controls.softDueDate.value).toBeUndefined();

            host.max.set(typed.add(1, 'day'));
            fixture.detectChanges();

            expect(host.form.valid).toBe(true);
            // Going valid is not enough: the model held undefined from the rejection, so the date has to be
            // handed over as well or the form would submit an empty date - the bug this validator exists for.
            expect(host.form.controls.softDueDate.value?.toISOString()).toBe(typed.toISOString());
        });

        it('keeps the rejection while the bound still excludes the typed date', () => {
            const typed = max.add(10, 'day');
            picker.updateField(typed.toDate());
            fixture.detectChanges();

            host.max.set(typed.subtract(1, 'day'));
            fixture.detectChanges();

            expect(host.form.controls.softDueDate.errors).toEqual({ invalidDate: true });
            expect(host.form.controls.softDueDate.value).toBeUndefined();
        });

        it('does not resurrect a rejected date after unparseable text replaced it', () => {
            picker.updateField(max.add(3, 'day').toDate());
            fixture.detectChanges();
            picker.updateField('asdasd');
            fixture.detectChanges();

            host.max.set(max.add(1, 'year'));
            fixture.detectChanges();

            expect(host.form.valid).toBe(false);
            expect(host.form.controls.softDueDate.value).toBeUndefined();
        });

        // reset() on a field that was still empty writes a value equal to the held one, so it takes writeValue's
        // idempotency early return. The pending entry has to be dropped before that return, or widening a bound
        // afterwards writes the discarded date into the form.
        it('does not resurrect a rejected date after a reset of a still-empty field', () => {
            picker.updateField(max.add(3, 'day').toDate());
            fixture.detectChanges();
            expect(host.form.valid).toBe(false);

            host.form.reset();
            fixture.detectChanges();
            expect(host.form.valid).toBe(true);

            host.max.set(max.add(1, 'year'));
            fixture.detectChanges();

            expect(host.form.valid).toBe(true);
            // reset() leaves the control at null rather than undefined; what matters is that the discarded
            // date did not come back.
            expect(host.form.controls.softDueDate.value ?? undefined).toBeUndefined();
        });

        it('does not resurrect a rejected date after the parent rewrites the value it already held', () => {
            const held = min.add(2, 'day');
            host.form.controls.softDueDate.setValue(held);
            fixture.detectChanges();
            picker.updateField(max.add(3, 'day').toDate());
            fixture.detectChanges();
            expect(host.form.valid).toBe(false);

            // The parent writes the same date it already had, which is an equal write.
            host.form.controls.softDueDate.setValue(held);
            fixture.detectChanges();

            host.max.set(max.add(1, 'year'));
            fixture.detectChanges();

            expect(host.form.controls.softDueDate.value?.toISOString()).toBe(held.toISOString());
        });

        it('does not resurrect a rejected date after the parent wrote a new value', () => {
            picker.updateField(max.add(3, 'day').toDate());
            fixture.detectChanges();
            const written = min.add(2, 'day');
            host.form.controls.softDueDate.setValue(written);
            fixture.detectChanges();

            host.max.set(max.add(1, 'year'));
            fixture.detectChanges();

            expect(host.form.valid).toBe(true);
            expect(host.form.controls.softDueDate.value?.toISOString()).toBe(written.toISOString());
        });

        it('keeps unparseable text and its error when a bound moves', () => {
            // The field holds a date, then the user types junk over it. `value()` still holds the old, in-range
            // date while the input shows the raw text, so a bound change must not recompute validity from it
            // and clear an error the user can still see.
            host.form.controls.softDueDate.setValue(min.add(5, 'day'));
            fixture.detectChanges();
            picker.updateField('asdasd');
            fixture.detectChanges();
            expect(host.form.valid).toBe(false);

            host.max.set(max.add(1, 'year'));
            fixture.detectChanges();

            expect(host.form.controls.softDueDate.errors).toEqual({ invalidDate: true });
            expect(host.form.valid).toBe(false);
        });

        it('recovers when a later write brings the value back inside the range', () => {
            host.form.controls.softDueDate.setValue(max.add(1, 'day'));
            fixture.detectChanges();
            expect(host.form.valid).toBe(false);

            host.form.controls.softDueDate.setValue(min.add(1, 'day'));
            fixture.detectChanges();

            expect(host.form.valid).toBe(true);
        });
    });

    // These drive the real input rather than updateField, because only actual typing leaves raw text in the
    // inner picker (keepInvalid). The display is what the user acts on, so it must never show a date the form
    // does not hold: that combination reads as "valid with a date" and submits nothing.
    describe('display stays in step with the model', () => {
        const shownDate = () => (fixture.nativeElement.querySelector('input.p-datepicker-input') as HTMLInputElement | null)?.value ?? '<no input>';

        const type = (text: string) => {
            const input = fixture.nativeElement.querySelector('input.p-datepicker-input') as HTMLInputElement;
            input.dispatchEvent(new KeyboardEvent('keydown', { key: '0', bubbles: true }));
            input.value = text;
            input.dispatchEvent(new Event('input', { bubbles: true }));
            fixture.detectChanges();
        };

        beforeEach(() => {
            host.max.set(dayjs('2026-06-30T00:00:00'));
            fixture.detectChanges();
        });

        it('clears a rejected date from the input when the control is reset', () => {
            type('05.07.2026');
            expect(shownDate()).toBe('05.07.2026');
            expect(host.form.valid).toBe(false);

            host.form.reset();
            fixture.detectChanges();

            expect(host.form.valid).toBe(true);
            expect(shownDate()).toBe('');
        });

        it('clears unparseable text from the input when the control is reset', () => {
            type('asdasd');
            expect(shownDate()).toBe('asdasd');
            expect(host.form.valid).toBe(false);

            host.form.reset();
            fixture.detectChanges();

            expect(host.form.valid).toBe(true);
            expect(shownDate()).toBe('');
        });

        it('shows the date the parent writes over rejected text', () => {
            type('05.07.2026');
            expect(host.form.valid).toBe(false);

            host.form.controls.softDueDate.setValue(dayjs('2026-06-10T00:00:00'));
            fixture.detectChanges();

            expect(host.form.valid).toBe(true);
            expect(shownDate()).toBe('10.06.2026');
        });
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
