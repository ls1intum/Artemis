import { LOCALE_ID, NgModule } from '@angular/core';
import { CommonModule, DatePipe, registerLocaleData } from '@angular/common';
import { HttpClientModule } from '@angular/common/http';
import { Title } from '@angular/platform-browser';
import locale from '@angular/common/locales/en';
import { ArTEMiSNotificationModule } from 'app/entities/notification/notification.module';
import { IntellijButtonComponent } from './intellij/intellij-button/intellij-button.component';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';

@NgModule({
    imports: [HttpClientModule, ArTEMiSNotificationModule, FontAwesomeModule, CommonModule],
    exports: [IntellijButtonComponent],
    declarations: [IntellijButtonComponent],
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
