import { Directive, computed, forwardRef, inject, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALIDATORS, NG_VALUE_ACCESSOR, ValidationErrors, Validator } from '@angular/forms';
import dayjs from 'dayjs/esm';
import { TumAetUiDatePickerComponent } from '@tumaet/ui-angular';

/**
 * Binds a `tumaet-ui-date-picker` to a reactive-forms control (`formControlName` / `formControl`).
 *
 * The picker is a signal-forms control and has no `ControlValueAccessor`, which is right for the kit. Forms that are
 * not on signal forms yet use this adapter instead of copying value and validity by hand into each form. It reads
 * and writes `dayjs` values, and reports text that is not a date as an `invalidDate` error, so a form with such text
 * in a field does not submit while the field silently shows nothing bound.
 *
 * It does not follow `control.disable()`, because the picker's `disabled` is an input and an adapter cannot set another
 * directive's input; bind `[disabled]` on the picker for that.
 *
 * Delete it together with the last reactive form that needs it.
 */
@Directive({
    selector: 'tumaet-ui-date-picker[formControlName], tumaet-ui-date-picker[formControl]',
    providers: [
        { provide: NG_VALUE_ACCESSOR, multi: true, useExisting: forwardRef(() => DatePickerControlDirective) },
        { provide: NG_VALIDATORS, multi: true, useExisting: forwardRef(() => DatePickerControlDirective) },
    ],
    // Host listeners rather than subscriptions: like a listener in a template, they mark the form that declares the picker for
    // check, which an OnPush or zoneless form needs to show the control's new value and validity. The compiler types the
    // `$event` of a host listener as a DOM event, so the handlers check what the picker's outputs actually carry.
    host: {
        '(valueChange)': 'onPickerValueChange($event)',
        '(inputValidityChange)': 'onTextValidityChange($event)',
        '(touch)': 'onTouched()',
    },
})
export class DatePickerControlDirective implements ControlValueAccessor, Validator {
    private readonly picker = inject(TumAetUiDatePickerComponent, { host: true });

    private readonly textValid = signal(true);
    private readonly hasTextError = computed(() => !this.textValid());

    // A model reports every write, including the one made by writeValue, which is the form's own change and not the user's.
    private writing = false;
    private onChange: (value: dayjs.Dayjs | undefined) => void = () => {};
    private onValidatorChange: () => void = () => {};
    protected onTouched: () => void = () => {};

    writeValue(value: dayjs.Dayjs | Date | string | null | undefined): void {
        const next = value == undefined ? undefined : dayjs(value);
        this.writing = true;
        try {
            // Not `picker.value.set`: that keeps invalid text in the field when the new value displays like the current one.
            this.picker.replaceValue(next?.isValid() ? next : undefined);
        } finally {
            this.writing = false;
        }
    }

    registerOnChange(fn: (value: dayjs.Dayjs | undefined) => void): void {
        this.onChange = fn;
    }

    registerOnTouched(fn: () => void): void {
        this.onTouched = fn;
    }

    registerOnValidatorChange(fn: () => void): void {
        this.onValidatorChange = fn;
    }

    validate(): ValidationErrors | null {
        return this.hasTextError() ? { invalidDate: true } : null;
    }

    protected onPickerValueChange(value: unknown): void {
        if (!this.writing) {
            this.onChange(dayjs.isDayjs(value) ? value : undefined);
        }
    }

    protected onTextValidityChange(valid: unknown): void {
        if (typeof valid === 'boolean' && this.textValid() !== valid) {
            this.textValid.set(valid);
            this.onValidatorChange();
        }
    }
}
