import './polyfills';
import 'app/shared/util/array.extension';
import 'app/shared/util/map.extension';
import 'app/shared/util/string.extension';
import 'app/core/config/dayjs';
import { ArtemisAppRoutingModule } from 'app/app-routing.module';
import { ArtemisComplaintsModule } from 'app/complaints/complaints.module';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { GuidedTourModule } from 'app/guided-tour/guided-tour.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { JhiMainComponent } from 'app/shared/layouts/main/main.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { UserSettingsModule } from 'app/shared/user-settings/user-settings.module';
import { ProdConfig } from './core/config/prod.config';
import { MonacoConfig } from 'app/core/config/monaco.config';
import { BrowserModule, bootstrapApplication } from '@angular/platform-browser';
import { APP_INITIALIZER, ErrorHandler, LOCALE_ID, importProvidersFrom } from '@angular/core';
import { DatePipe, registerLocaleData } from '@angular/common';
import { HTTP_INTERCEPTORS, HttpClient, provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { Title } from '@angular/platform-browser';
import { AuthExpiredInterceptor } from 'app/core/interceptor/auth-expired.interceptor';
import { ErrorHandlerInterceptor } from 'app/core/interceptor/errorhandler.interceptor';
import { NotificationInterceptor } from 'app/core/interceptor/notification.interceptor';
import { NgbDateAdapter, NgbDatepickerConfig, NgbTooltipConfig } from '@ng-bootstrap/ng-bootstrap';
import { SessionStorageService, provideNgxWebstorage, withLocalStorage, withNgxWebstorageConfig, withSessionStorage } from 'ngx-webstorage';
import { SentryErrorHandler } from 'app/core/sentry/sentry.error-handler';
import { LoadingNotificationInterceptor } from 'app/shared/notification/loading-notification/loading-notification.interceptor';
import { BrowserFingerprintInterceptor } from 'app/core/interceptor/browser-fingerprint.interceptor.service';
import { ArtemisVersionInterceptor, WINDOW_INJECTOR_TOKEN } from 'app/core/interceptor/artemis-version.interceptor';
import { NgbDateDayjsAdapter } from 'app/core/config/datepicker-adapter';
import { TraceService } from '@sentry/angular';
import { Router, RouterModule } from '@angular/router';
import { FaIconLibrary } from '@fortawesome/angular-fontawesome';
import { artemisIconPack } from '../content/icons/icons';
import { MissingTranslationHandler, TranslateLoader, TranslateModule, TranslateService } from '@ngx-translate/core';
import { JhiLanguageHelper } from './core/language/language.helper';
import dayjs from 'dayjs/esm';
import { missingTranslationHandler, translatePartialLoader } from './core/config/translation.config';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { ServiceWorkerModule } from '@angular/service-worker';
import locale from '@angular/common/locales/en';
import { ScrollingModule } from '@angular/cdk/scrolling';

ProdConfig();
MonacoConfig();

bootstrapApplication(JhiMainComponent, {
    providers: [
        importProvidersFrom(
            ArtemisAppRoutingModule,
            ArtemisComplaintsModule,
            ArtemisHeaderExercisePageWithDetailsModule,
            ArtemisSharedComponentModule,
            ArtemisSharedModule,
            BrowserAnimationsModule,
            BrowserModule,
            GuidedTourModule,
            RouterModule,
            ScrollingModule,
            UserSettingsModule,
            // This enables service worker (PWA)
            ServiceWorkerModule.register('ngsw-worker.js', { enabled: true }),
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
        ),
        provideHttpClient(withInterceptorsFromDi()),
        provideNgxWebstorage(withNgxWebstorageConfig({ prefix: 'jhi', separator: '-' }), withLocalStorage(), withSessionStorage()),
        Title,
        { provide: LOCALE_ID, useValue: 'en' },
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
    .then((app) => {
        const library = app.injector.get(FaIconLibrary);
        library.addIconPacks(artemisIconPack);
        const dpConfig = app.injector.get(NgbDatepickerConfig);
        const tooltipConfig = app.injector.get(NgbTooltipConfig);
        const translateService = app.injector.get(TranslateService);
        const languageHelper = app.injector.get(JhiLanguageHelper);
        const sessionStorageService = app.injector.get(SessionStorageService);

        // Perform initialization logic
        registerLocaleData(locale);
        dpConfig.minDate = { year: dayjs().subtract(100, 'year').year(), month: 1, day: 1 };
        translateService.setDefaultLang('en');
        const languageKey = sessionStorageService.retrieve('locale') || languageHelper.determinePreferredLanguage();
        translateService.use(languageKey);
        tooltipConfig.container = 'body';
    })
    .catch((err) => console.error(err));
