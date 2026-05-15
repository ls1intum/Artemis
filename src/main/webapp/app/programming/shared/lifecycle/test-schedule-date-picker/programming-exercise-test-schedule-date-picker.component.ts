import { Component, Input, forwardRef, input, output, viewChild } from '@angular/core';
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
    readonly dateInput = viewChild.required<NgModel>('dateInput');
    // TODO: Skipped for migration because:
    //  Your application code writes to the input. This prevents migration.
    @Input() selectedDate?: Date;
    readonly startAt = input<dayjs.Dayjs>();
    readonly min = input<dayjs.Dayjs>();
    readonly max = input<dayjs.Dayjs>();
    readonly label = input<string>(undefined!);
    // TODO: Skipped for migration because:
    //  This input is used in a control flow expression (e.g. `@if` or `*ngIf`)
    //  and migrating would break narrowing currently.
    @Input() tooltipText: string;
    // TODO: Skipped for migration because:
    //  This input is used in a control flow expression (e.g. `@if` or `*ngIf`)
    //  and migrating would break narrowing currently.
    @Input() readOnly: boolean;
    readonly onDateReset = output();

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
