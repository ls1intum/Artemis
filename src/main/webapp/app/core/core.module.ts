import { LOCALE_ID, NgModule } from '@angular/core';
import { DatePipe, registerLocaleData } from '@angular/common';
import { HttpClient, HttpClientModule } from '@angular/common/http';
import { Title } from '@angular/platform-browser';
import locale from '@angular/common/locales/en';
import { MissingTranslationHandler, TranslateLoader, TranslateModule } from '@ngx-translate/core';
import { JhiConfigService, missingTranslationHandler, translatePartialLoader } from 'ng-jhipster';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';

@NgModule({
    imports: [HttpClientModule],
    exports: [],
    declarations: [],
    providers: [
        Title,
        {
            provide: LOCALE_ID,
            useValue: 'en',
        },
        DatePipe,
        JhiWebsocketService,
    ],
})
export class ArtemisCoreModule {
    constructor() {
        registerLocaleData(locale);
    }
}
