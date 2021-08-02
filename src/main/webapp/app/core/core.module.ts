import { ErrorHandler, LOCALE_ID, NgModule } from '@angular/core';
import { DatePipe, registerLocaleData } from '@angular/common';
import { HTTP_INTERCEPTORS, HttpClient, HttpClientModule } from '@angular/common/http';
import { Title } from '@angular/platform-browser';
import { AuthInterceptor } from 'app/core/interceptor/auth.interceptor';
import { AuthExpiredInterceptor } from 'app/core/interceptor/auth-expired.interceptor';
import { ErrorHandlerInterceptor } from 'app/core/interceptor/errorhandler.interceptor';
import { NotificationInterceptor } from 'app/core/interceptor/notification.interceptor';
import { JhiConfigService, JhiLanguageService, NgJhipsterModule } from 'ng-jhipster';
import { NgbDateAdapter, NgbDatepickerConfig } from '@ng-bootstrap/ng-bootstrap';
import * as moment from 'moment';
import { NgxWebstorageModule } from 'ngx-webstorage';
import { DifferencePipe, MomentModule } from 'ngx-moment';
import { NgbDateMomentAdapter } from 'app/shared/util/datepicker-adapter';
import { FaIconLibrary } from '@fortawesome/angular-fontawesome';
import { fas } from '@fortawesome/free-solid-svg-icons';
import locale from '@angular/common/locales/en';
import { fontAwesomeIcons } from 'app/core/icons/font-awesome-icons';
import { MissingTranslationHandler, TranslateLoader, TranslateModule } from '@ngx-translate/core';
import { SentryErrorHandler } from 'app/core/sentry/sentry.error-handler';
import { RepositoryInterceptor } from 'app/exercises/shared/result/repository.service';
import { CookieService } from 'ngx-cookie-service';
import { LoadingNotificationInterceptor } from 'app/shared/notification/loading-notification/loading-notification.interceptor';
import { BrowserFingerprintInterceptor } from 'app/core/interceptor/browser-fingerprint.interceptor.service';
import { ArtemisVersionInterceptor } from 'app/core/interceptor/artemis-version.interceptor';
import { missingTranslationHandler, translatePartialLoader } from './config/translation.config';

@NgModule({
    imports: [
        HttpClientModule,
        NgxWebstorageModule.forRoot({ prefix: 'jhi', separator: '-' }),
        /**
         * @external MomentModule is a date library for parsing, validating, manipulating, and formatting dates.
         */
        MomentModule,
        NgJhipsterModule.forRoot({
            // set below to true to make alerts look like toast
            alertAsToast: false,
            alertTimeout: 8000,
            i18nEnabled: true,
            defaultI18nLang: 'en',
        }),
        TranslateModule.forRoot({
            loader: {
                provide: TranslateLoader,
                useFactory: translatePartialLoader,
                deps: [HttpClient],
            },
            missingTranslationHandler: {
                provide: MissingTranslationHandler,
                useFactory: missingTranslationHandler,
                deps: [JhiConfigService],
            },
        }),
    ],
    providers: [
        Title,
        {
            provide: LOCALE_ID,
            useValue: 'en',
        },
        { provide: NgbDateAdapter, useClass: NgbDateMomentAdapter },
        { provide: ErrorHandler, useClass: SentryErrorHandler },
        DatePipe,
        DifferencePipe,
        CookieService,
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
    constructor(iconLibrary: FaIconLibrary, dpConfig: NgbDatepickerConfig, languageService: JhiLanguageService) {
        registerLocaleData(locale);
        iconLibrary.addIconPacks(fas);
        iconLibrary.addIcons(...fontAwesomeIcons);
        dpConfig.minDate = { year: moment().year() - 100, month: 1, day: 1 };
        languageService.init();
    }
}
