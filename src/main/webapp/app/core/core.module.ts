import { APP_INITIALIZER, ErrorHandler, LOCALE_ID, NgModule } from '@angular/core';
import { DatePipe, registerLocaleData } from '@angular/common';
import { HTTP_INTERCEPTORS, HttpClient, HttpClientModule } from '@angular/common/http';
import { Title } from '@angular/platform-browser';
import { AuthInterceptor } from 'app/core/interceptor/auth.interceptor';
import { AuthExpiredInterceptor } from 'app/core/interceptor/auth-expired.interceptor';
import { ErrorHandlerInterceptor } from 'app/core/interceptor/errorhandler.interceptor';
import { NotificationInterceptor } from 'app/core/interceptor/notification.interceptor';
import { NgbDateAdapter, NgbDatepickerConfig, NgbTooltipConfig } from '@ng-bootstrap/ng-bootstrap';
import { NgxWebstorageModule, SessionStorageService } from 'ngx-webstorage';
import locale from '@angular/common/locales/en';
import { MissingTranslationHandler, TranslateLoader, TranslateModule, TranslateService } from '@ngx-translate/core';
import { SentryErrorHandler } from 'app/core/sentry/sentry.error-handler';
import { RepositoryInterceptor } from 'app/exercises/shared/result/repository.service';
import { LoadingNotificationInterceptor } from 'app/shared/notification/loading-notification/loading-notification.interceptor';
import { BrowserFingerprintInterceptor } from 'app/core/interceptor/browser-fingerprint.interceptor.service';
import { ArtemisVersionInterceptor, WINDOW_INJECTOR_TOKEN } from 'app/core/interceptor/artemis-version.interceptor';
import { missingTranslationHandler, translatePartialLoader } from './config/translation.config';
import dayjs from 'dayjs/esm';
import './config/dayjs';
import { NgbDateDayjsAdapter } from 'app/core/config/datepicker-adapter';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { TraceService } from '@sentry/angular';
import { Router } from '@angular/router';

@NgModule({
    imports: [
        HttpClientModule,
        NgxWebstorageModule.forRoot({ prefix: 'jhi', separator: '-' }),
        TranslateModule.forRoot({
            loader: {
                provide: TranslateLoader,
                useFactory: translatePartialLoader,
                deps: [HttpClient],
            },
            missingTranslationHandler: {
                provide: MissingTranslationHandler,
                useFactory: missingTranslationHandler,
            },
        }),
    ],
    providers: [
        Title,
        {
            provide: LOCALE_ID,
            useValue: 'en',
        },
        { provide: NgbDateAdapter, useClass: NgbDateDayjsAdapter },
        { provide: TraceService, deps: [Router] },
        { provide: ErrorHandler, useClass: SentryErrorHandler },
        { provide: WINDOW_INJECTOR_TOKEN, useValue: window },
        DatePipe,
        {
            provide: APP_INITIALIZER,
            useFactory: () => () => {},
            deps: [TraceService],
            multi: true,
        },
        /**
         * @description Interceptor declarations:
         * Interceptors are located at 'blocks/interceptor/.
         * All of them implement the HttpInterceptor interface.
         * They can be used to modify API calls or trigger additional function calls.
         * Most interceptors will transform the outgoing request before passing it to
         * the next interceptor in the chain, by calling next.handle(transformedReq).
         * Documentation: https://angular.io/api/common/http/HttpInterceptor
         */
        {
            provide: HTTP_INTERCEPTORS,
            useClass: AuthInterceptor,
            multi: true,
        },
        {
            provide: HTTP_INTERCEPTORS,
            useClass: AuthExpiredInterceptor,
            multi: true,
        },
        {
            provide: HTTP_INTERCEPTORS,
            useClass: ErrorHandlerInterceptor,
            multi: true,
        },
        {
            provide: HTTP_INTERCEPTORS,
            useClass: BrowserFingerprintInterceptor,
            multi: true,
        },
        {
            provide: HTTP_INTERCEPTORS,
            useClass: NotificationInterceptor,
            multi: true,
        },
        {
            provide: HTTP_INTERCEPTORS,
            useClass: RepositoryInterceptor,
            multi: true,
        },
        {
            provide: HTTP_INTERCEPTORS,
            useClass: LoadingNotificationInterceptor,
            multi: true,
        },
        {
            provide: HTTP_INTERCEPTORS,
            useClass: ArtemisVersionInterceptor,
            multi: true,
        },
    ],
})
export class ArtemisCoreModule {
    constructor(
        dpConfig: NgbDatepickerConfig,
        tooltipConfig: NgbTooltipConfig,
        translateService: TranslateService,
        languageHelper: JhiLanguageHelper,
        sessionStorageService: SessionStorageService,
    ) {
        registerLocaleData(locale);
        dpConfig.minDate = { year: dayjs().subtract(100, 'year').year(), month: 1, day: 1 };
        translateService.setDefaultLang('en');
        const languageKey = sessionStorageService.retrieve('locale') || languageHelper.determinePreferredLanguage();
        translateService.use(languageKey);
        tooltipConfig.container = 'body';
    }
}
