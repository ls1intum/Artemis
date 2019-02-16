import { CommonModule } from '@angular/common';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { NgModule } from '@angular/core';
import { OwlDateTimeModule, OwlNativeDateTimeModule, OWL_DATE_TIME_FORMATS } from 'ng-pick-datetime';
import { OwlMomentDateTimeModule } from 'ng-pick-datetime-moment';

import { DatePipeModule } from '../../components/pipes';
import { FormDateTimePickerComponent } from './date-time-picker.component';

export const MY_MOMENT_FORMATS = {
    parseInput: 'l LT',
    fullPickerInput: 'MMM D, YYYY, h:mm:ss A',
    datePickerInput: 'l',
    timePickerInput: 'LT',
    monthYearLabel: 'MMM YYYY',
    dateA11yLabel: 'LL',
    monthYearA11yLabel: 'MMMM YYYY'
};
@NgModule({
    imports: [
        CommonModule,
        FormsModule,
        DatePipeModule,
        OwlDateTimeModule,
        OwlNativeDateTimeModule,
        OwlMomentDateTimeModule,
        ReactiveFormsModule,
        FontAwesomeModule
    ],
    exports: [FormDateTimePickerComponent],
    declarations: [FormDateTimePickerComponent],
    providers: [{ provide: OWL_DATE_TIME_FORMATS, useValue: MY_MOMENT_FORMATS }]
})
export class FormDateTimePickerModule {}
