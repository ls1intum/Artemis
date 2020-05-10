import { Component, ElementRef, EventEmitter, forwardRef, Input, Output, ViewChild } from '@angular/core';
import { isDate, Moment } from 'moment';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

@Component({
    selector: 'jhi-programming-exercise-test-schedule-date-picker',
    templateUrl: './programming-exercise-test-schedule-date-picker.component.html',
    providers: [
        {
            provide: NG_VALUE_ACCESSOR,
            multi: true,
            useExisting: forwardRef(() => ProgrammingExerciseTestScheduleDatePickerComponent),
        },
    ],
    styleUrls: ['./programming-exercise-test-schedule-picker.scss'],
})
export class ProgrammingExerciseTestScheduleDatePickerComponent implements ControlValueAccessor {
    @ViewChild('dateInput', { static: false }) dateInput: ElementRef;
    @Input() selectedDate: Date | null;
    @Input() startAt: Moment | null;
    @Input() min: Moment;
    @Input() max: Moment;
    @Input() label: string;
    @Input() tooltipText: string;
    @Output() onDateReset = new EventEmitter();

    _onChange: any = () => {};

    registerOnChange(fn: any): void {
        this._onChange = fn;
    }

    registerOnTouched(): void {}

    setDisabledState(): void {}

    writeValue(obj: any): void {
        if (obj !== undefined && this.selectedDate !== obj) {
            this.selectedDate = !obj ? null : isDate(obj) ? obj : obj.toDate();
            this._onChange(obj);
        }
    }

    /**
     * Resets the date to null and informs parent components that the date has been reset.
     * This makes it easier to also reset date, that can only be selected if the current date is not null
     */
    resetDate() {
        this.writeValue(null);
        this.onDateReset.emit();
    }
}
