import { LOCALE_ID, NgModule } from '@angular/core';
import { DatePipe, registerLocaleData } from '@angular/common';
import { HttpClientModule } from '@angular/common/http';
import { Title } from '@angular/platform-browser';
import locale from '@angular/common/locales/en';
import { FeatureToggleModule } from 'app/core/feature-toggle/feature-toggle.module';

@NgModule({
    imports: [HttpClientModule, FeatureToggleModule.forRoot()],
    exports: [],
    declarations: [],
    providers: [
        Title,
        {
            provide: LOCALE_ID,
            useValue: 'en',
        },
        DatePipe,
    ],
})
export class ArtemisCoreModule {
    constructor() {
        registerLocaleData(locale);
    }
}
