import './polyfills';
import 'app/shared/util/array.extension';
import 'app/shared/util/map.extension';
import 'app/shared/util/string.extension';
import 'app/core/config/dayjs';
import { ScrollingModule } from '@angular/cdk/scrolling';
import { DatePipe } from '@angular/common';
import { HTTP_INTERCEPTORS, HttpClient, provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { ApplicationConfig, ErrorHandler, LOCALE_ID, importProvidersFrom, inject, provideAppInitializer } from '@angular/core';
import { BrowserModule, Title } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { Router, RouterModule, provideRouter, withRouterConfig } from '@angular/router';
import { ServiceWorkerModule } from '@angular/service-worker';
import { NgbDateAdapter } from '@ng-bootstrap/ng-bootstrap';
import { MissingTranslationHandler, TranslateLoader, TranslateModule } from '@ngx-translate/core';
import * as Sentry from '@sentry/angular';
import { TraceService } from '@sentry/angular';
import routes from 'app/app.routes';
import { NgbDateDayjsAdapter } from 'app/core/config/datepicker-adapter';
import { missingTranslationHandler, translatePartialLoader } from 'app/core/config/translation.config';
import { ArtemisVersionInterceptor, WINDOW_INJECTOR_TOKEN } from 'app/core/interceptor/artemis-version.interceptor';
import { AuthExpiredInterceptor } from 'app/core/interceptor/auth-expired.interceptor';
import { BrowserFingerprintInterceptor } from 'app/core/interceptor/browser-fingerprint.interceptor.service';
import { ErrorHandlerInterceptor } from 'app/core/interceptor/errorhandler.interceptor';
import { NotificationInterceptor } from 'app/core/interceptor/notification.interceptor';
import { SentryErrorHandler } from 'app/core/sentry/sentry.error-handler';

import { LoadingNotificationInterceptor } from 'app/shared/notification/loading-notification/loading-notification.interceptor';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';

import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { provideNgxWebstorage, withLocalStorage, withNgxWebstorageConfig, withSessionStorage } from 'ngx-webstorage';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

export function initOrionConnector(connector: OrionConnectorService) {
    return () => OrionConnectorService.initConnector(connector);
}

export const appConfig: ApplicationConfig = {
    providers: [
        ArtemisTranslatePipe,
        importProvidersFrom(
            // TODO: we should exclude modules here in the future

            BrowserAnimationsModule,
            BrowserModule,
            RouterModule,
            ScrollingModule,
            OwlNativeDateTimeModule,
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

        // TODO: we should add withComponentInputBinding here
        //  this would set non-route inputs to undefined, which not all components can handle, currently
        //  see https://angular.dev/api/router/withComponentInputBinding?tab=usage-notes
        //  provideRouter(routes, withComponentInputBinding(), withRouterConfig({ onSameUrlNavigation: 'reload' })),
        provideRouter(routes, withRouterConfig({ onSameUrlNavigation: 'reload' })),
        // This enables service worker (PWA)
        importProvidersFrom(ServiceWorkerModule.register('ngsw-worker.js', { enabled: true })),
        provideHttpClient(withInterceptorsFromDi()),
        provideNgxWebstorage(withNgxWebstorageConfig({ prefix: 'jhi', separator: '-' }), withLocalStorage(), withSessionStorage()),
        Title,
        { provide: LOCALE_ID, useValue: 'en' },
        { provide: NgbDateAdapter, useClass: NgbDateDayjsAdapter },
        { provide: Sentry.TraceService, deps: [Router] },
        { provide: ErrorHandler, useClass: SentryErrorHandler },
        { provide: WINDOW_INJECTOR_TOKEN, useValue: window },
        DatePipe,
        provideAppInitializer(() => {
            inject(TraceService);
            // Ensure the service is initialized before any routing happens
            inject(ArtemisNavigationUtilService);
            // Required, otherwise Orion will not work at all
            initOrionConnector(inject(OrionConnectorService));
        }),
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
};
