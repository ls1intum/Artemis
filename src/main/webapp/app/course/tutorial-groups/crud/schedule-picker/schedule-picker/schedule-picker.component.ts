import { Component, forwardRef } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

@Component({
    selector: 'jhi-schedule-picker',
    templateUrl: './schedule-picker.component.html',
    styleUrls: ['./schedule-picker.component.scss'],
    providers: [
        {
            provide: NG_VALUE_ACCESSOR,
            useExisting: forwardRef(() => SchedulePickerComponent),
            multi: true,
        },
    ],
})
export class SchedulePickerComponent implements ControlValueAccessor {
    constructor() {}

    // ======= ControlValueAccessor Implementation START =======
    writeValue(obj: any): void {
        throw new Error('Method not implemented.');
    }
    registerOnChange(fn: any): void {
        throw new Error('Method not implemented.');
    }
    registerOnTouched(fn: any): void {
        throw new Error('Method not implemented.');
    }
    // ======= ControlValueAccessor Implementation END =======
}
