import { NgModule } from '@angular/core';
import { DatePipe } from './format-date.pipe';

@NgModule({
    declarations: [
        DatePipe
    ],
    exports: [
        DatePipe
    ]
})
export class DatePipeModule {}
