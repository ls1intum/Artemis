import { NgModule } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { FormDateTimePickerComponent } from './date-time-picker.component';
import { DatePipeModule } from '../../components/pipes';
import { OwlDateTimeModule, OwlNativeDateTimeModule } from 'ng-pick-datetime';

@NgModule({
    imports: [CommonModule, FormsModule, DatePipeModule, OwlDateTimeModule, OwlNativeDateTimeModule],
    exports: [FormDateTimePickerComponent],
    declarations: [FormDateTimePickerComponent],
    providers: []
})
export class FormDateTimePickerModule {}
