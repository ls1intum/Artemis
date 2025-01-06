import './polyfills';
import 'app/shared/util/array.extension';
import 'app/shared/util/map.extension';
import 'app/shared/util/string.extension';
import 'app/core/config/dayjs';
import { ErrorHandler, provideAppInitializer } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HTTP_INTERCEPTORS } from '@angular/common/http';
import routes from 'app/app.routes';
import { NgbDateDayjsAdapter } from 'app/core/config/datepicker-adapter';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { provideNgxWebstorage, withLocalStorage, withNgxWebstorageConfig, withSessionStorage } from 'ngx-webstorage';
import { TraceService } from '@sentry/angular';
import * as Sentry from '@sentry/angular';
import { ApplicationConfig, LOCALE_ID, importProvidersFrom, inject } from '@angular/core';
import { BrowserModule, Title } from '@angular/platform-browser';
import { NavigationError, Router, RouterFeatures, RouterModule, provideRouter, withComponentInputBinding, withNavigationErrorHandler, withRouterConfig } from '@angular/router';
import { HttpClient, provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NgbDateAdapter } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisComplaintsModule } from 'app/complaints/complaints.module';
import { missingTranslationHandler, translatePartialLoader } from 'app/core/config/translation.config';
import { ArtemisVersionInterceptor, WINDOW_INJECTOR_TOKEN } from 'app/core/interceptor/artemis-version.interceptor';
import { AuthExpiredInterceptor } from 'app/core/interceptor/auth-expired.interceptor';
import { BrowserFingerprintInterceptor } from 'app/core/interceptor/browser-fingerprint.interceptor.service';
import { ErrorHandlerInterceptor } from 'app/core/interceptor/errorhandler.interceptor';
import { NotificationInterceptor } from 'app/core/interceptor/notification.interceptor';
import { SentryErrorHandler } from 'app/core/sentry/sentry.error-handler';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { GuidedTourModule } from 'app/guided-tour/guided-tour.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { LoadingNotificationInterceptor } from 'app/shared/notification/loading-notification/loading-notification.interceptor';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { UserSettingsModule } from 'app/shared/user-settings/user-settings.module';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { ScrollingModule } from '@angular/cdk/scrolling';
import { ServiceWorkerModule } from '@angular/service-worker';
import { MissingTranslationHandler, TranslateLoader, TranslateModule } from '@ngx-translate/core';

export function initOrionConnector(connector: OrionConnectorService) {
    return () => OrionConnectorService.initConnector(connector);
}

// TODO: double check whether we want withNavigationErrorHandler or not
const routerFeatures: RouterFeatures[] = [
    withComponentInputBinding(),
    withRouterConfig({ onSameUrlNavigation: 'reload' }),
    withNavigationErrorHandler((e: NavigationError) => {
        const router = inject(Router);
        if (e.error.status === 403) {
            router.navigate(['/accessdenied']);
        } else if (e.error.status === 404) {
            router.navigate(['/404']);
        } else if (e.error.status === 401) {
            router.navigate(['/login']);
        } else {
            router.navigate(['/error']);
        }
    }),
];

export const appConfig: ApplicationConfig = {
    providers: [
        importProvidersFrom(
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

        provideRouter(routes, ...routerFeatures),
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
