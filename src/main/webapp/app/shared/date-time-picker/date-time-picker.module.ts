import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { NgModule } from '@angular/core';
import { OWL_DATE_TIME_FORMATS, OwlDateTimeModule, OwlNativeDateTimeModule } from 'ng-pick-datetime';
import { FormDateTimePickerComponent } from './date-time-picker.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

// It would be nice to use the dayjs adapter for ng-pick-datetime: https://danielykpan.github.io/date-time-picker/
// However atm there is a compiler issue in angular 7 that conflicts with the compilation of this module: https://github.com/angular/angular/issues/23609
export const MY_NATIVE_FORMATS = {
    fullPickerInput: { year: 'numeric', month: 'short', day: 'numeric', hour: 'numeric', minute: 'numeric', second: 'numeric' },
    datePickerInput: { year: 'numeric', month: 'numeric', day: 'numeric' },
    timePickerInput: { hour: 'numeric', minute: 'numeric' },
    monthYearLabel: { year: 'numeric', month: 'short' },
    dateA11yLabel: { year: 'numeric', month: 'long', day: 'numeric' },
    monthYearA11yLabel: { year: 'numeric', month: 'long' },
};
@NgModule({
    imports: [CommonModule, FormsModule, OwlDateTimeModule, OwlNativeDateTimeModule, ReactiveFormsModule, ArtemisSharedModule],
    exports: [FormDateTimePickerComponent],
    declarations: [FormDateTimePickerComponent],
    providers: [{ provide: OWL_DATE_TIME_FORMATS, useValue: MY_NATIVE_FORMATS }],
})
export class FormDateTimePickerModule {}
