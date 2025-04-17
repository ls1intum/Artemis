import { Component, EventEmitter, Input, Output, ViewChild, forwardRef } from '@angular/core';
import { ControlValueAccessor, FormsModule, NG_VALUE_ACCESSOR, NgModel } from '@angular/forms';
import dayjs from 'dayjs/esm';
import { isDate } from 'app/shared/util/utils';
import { faCalendarCheck, faCalendarMinus, faCalendarPlus } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { OwlDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';

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
    imports: [TranslateDirective, HelpIconComponent, FormsModule, OwlDateTimeModule, FaIconComponent, ArtemisDatePipe],
})
export class ProgrammingExerciseTestScheduleDatePickerComponent implements ControlValueAccessor {
    @ViewChild('dateInput', { static: false }) dateInput: NgModel;
    @Input() selectedDate?: Date;
    @Input() startAt?: dayjs.Dayjs;
    @Input() min?: dayjs.Dayjs;
    @Input() max?: dayjs.Dayjs;
    @Input() label: string;
    @Input() tooltipText: string;
    @Input() readOnly: boolean;
    @Output() onDateReset = new EventEmitter();

    // Icons
    faCalendarMinus = faCalendarMinus;
    faCalendarCheck = faCalendarCheck;
    faCalendarPlus = faCalendarPlus;

    _onChange: any = () => {};

    registerOnChange(fn: any): void {
        this._onChange = fn;
    }

    registerOnTouched(): void {}

    setDisabledState(): void {}

    writeValue(obj: any): void {
        if (this.selectedDate !== obj) {
            this.selectedDate = !obj || isDate(obj) ? obj : obj.toDate();
            this.selectedDate?.setSeconds(0, 0);
            this._onChange(obj);
        }
    }

    /**
     * Resets the date and informs parent components that the date has been reset.
     * This makes it easier to also reset date, that can only be selected if the current date is not set
     */
    resetDate() {
        this.writeValue(undefined);
        this.onDateReset.emit();
    }
}
