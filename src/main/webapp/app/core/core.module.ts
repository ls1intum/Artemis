import { LOCALE_ID, NgModule } from '@angular/core';
import { DatePipe, registerLocaleData } from '@angular/common';
import { HttpClientModule } from '@angular/common/http';
import { Title } from '@angular/platform-browser';
import locale from '@angular/common/locales/en';
import { ArTEMiSNotificationModule } from 'app/entities/notification/notification.module';
import { IntellijDirective } from './intellij/intellij.directive';

@NgModule({
    imports: [HttpClientModule, ArTEMiSNotificationModule],
    exports: [IntellijDirective],
    declarations: [IntellijDirective],
    providers: [
        Title,
        {
            provide: LOCALE_ID,
            useValue: 'en',
        },
        DatePipe,
    ],
})
export class ArTEMiSCoreModule {
    constructor() {
        registerLocaleData(locale);
    }
}
