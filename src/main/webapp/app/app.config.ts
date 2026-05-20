import './polyfills';
import 'app/shared/util/array.extension';
import 'app/shared/util/map.extension';
import 'app/shared/util/string.extension';
import 'app/core/config/dayjs';
import { ScrollingModule } from '@angular/cdk/scrolling';
import { DatePipe } from '@angular/common';
import { HTTP_INTERCEPTORS, provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { ApplicationConfig, ErrorHandler, LOCALE_ID, importProvidersFrom, inject, provideAppInitializer, provideZoneChangeDetection } from '@angular/core';
import { BrowserModule, Title } from '@angular/platform-browser';
import { Router, provideRouter, withRouterConfig } from '@angular/router';
import { ServiceWorkerModule } from '@angular/service-worker';
import { NgbDateAdapter } from '@ng-bootstrap/ng-bootstrap';
import { MissingTranslationHandler, provideTranslateService } from '@ngx-translate/core';
import * as Sentry from '@sentry/angular';
import { TraceService } from '@sentry/angular';
import routes from 'app/app.routes';
import { NgbDateDayjsAdapter } from 'app/core/config/datepicker-adapter';
import { missingTranslationHandler, translateHttpLoaderProviders } from 'app/core/config/translation.config';
import { ArtemisVersionInterceptor, WINDOW_INJECTOR_TOKEN } from 'app/core/interceptor/artemis-version.interceptor';
import { AuthExpiredInterceptor } from 'app/core/interceptor/auth-expired.interceptor';
import { BrowserFingerprintInterceptor } from 'app/core/interceptor/browser-fingerprint.interceptor.service';
import { ErrorHandlerInterceptor } from 'app/core/interceptor/errorhandler.interceptor';
import { NotificationInterceptor } from 'app/core/interceptor/notification.interceptor';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { AccountService } from 'app/core/auth/account.service';
import { AuthServerProvider } from 'app/core/auth/auth-jwt.service';
import { TranslateService } from '@ngx-translate/core';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { lastValueFrom } from 'rxjs';
import { SentryErrorHandler } from 'app/core/sentry/sentry.error-handler';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { LoadingNotificationInterceptor } from 'app/core/loading-notification/loading-notification.interceptor';
import { ArtemisNavigationUtilService } from 'app/shared/util/navigation.utils';
import { Configuration } from 'app/openapi/configuration';
import { providePrimeNG } from 'primeng/config';
import { DialogService } from 'primeng/dynamicdialog';
import { AuraArtemis } from './primeng-artemis-theme';

export const appConfig: ApplicationConfig = {
    providers: [
        ArtemisTranslatePipe,
        DialogService,
        importProvidersFrom(
            // TODO: we should exclude modules here in the future
            BrowserModule,
            ScrollingModule,
            OwlNativeDateTimeModule,
        ),
        provideTranslateService({
            loader: translateHttpLoaderProviders,
            missingTranslationHandler: {
                provide: MissingTranslationHandler,
                useFactory: missingTranslationHandler,
            },
        }),
        provideZoneChangeDetection(),

        // TODO: we should add withComponentInputBinding here
        //  this would set non-route inputs to undefined, which not all components can handle, currently
        //  see https://angular.dev/api/router/withComponentInputBinding?tab=usage-notes
        //  provideRouter(routes, withComponentInputBinding(), withRouterConfig({ onSameUrlNavigation: 'reload' })),
        provideRouter(routes, withRouterConfig({ onSameUrlNavigation: 'reload' })),
        // This enables service worker (PWA)
        importProvidersFrom(ServiceWorkerModule.register('ngsw-worker.js', { enabled: true })),
        provideHttpClient(withInterceptorsFromDi()),
        Title,
        { provide: LOCALE_ID, useValue: 'en' },
        { provide: NgbDateAdapter, useClass: NgbDateDayjsAdapter },
        { provide: Sentry.TraceService, deps: [Router] },
        { provide: ErrorHandler, useClass: SentryErrorHandler },
        { provide: WINDOW_INJECTOR_TOKEN, useValue: window },
        DatePipe,
        provideAppInitializer(() => {
            const profileService = inject(ProfileService);
            const accountService = inject(AccountService);
            const authServerProvider = inject(AuthServerProvider);
            const translateService = inject(TranslateService);
            const sessionStorageService = inject(SessionStorageService);
            const languageHelper = inject(JhiLanguageHelper);
            inject(TraceService);
            // Ensure the service is initialized before any routing happens
            inject(ArtemisNavigationUtilService);
            // If the IdP just redirected the user back to Artemis, complete the SAML2 second-step
            // exchange (turn the SAML2 HttpSession into an Artemis JWT cookie) before resolving the
            // user identity. This way the regular route guards see an authenticated user and route
            // them to /courses, instead of the public landing page rendered for unauthenticated visitors.
            // Match the cookie name exactly so a name that merely ends with 'SAML2flow' cannot trigger this branch.
            const hasSaml2FlowCookie = /(?:^|;\s*)SAML2flow=/.test(document.cookie);
            const completeSaml2 = hasSaml2FlowCookie
                ? lastValueFrom(authServerProvider.loginSAML2(true))
                      // The .catch is load-bearing: Promise.all below short-circuits on the first
                      // rejection, so any error here would abort APP_INITIALIZER and prevent the SPA
                      // from booting. We log so the failure is observable but recover by rendering
                      // the landing page (or sign-in if the user navigates there manually).
                      .catch((error) => {
                          // eslint-disable-next-line no-undef
                          console.warn('SAML2 second-step exchange failed during app initialization', error);
                          return undefined;
                      })
                      .finally(() => {
                          // Path=/ must match the path the cookie was set with in Saml2LoginComponent so the
                          // deletion reliably removes it regardless of the document path the SPA boots from.
                          document.cookie = 'SAML2flow=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/; SameSite=Lax;';
                      })
                : Promise.resolve();
            // Block bootstrap on translation loading so the first render never shows raw
            // `translation-not-found[...]` keys. `artemisTranslate` and `translate` pipes
            // resolve synchronously via `instant()`, which falls back to the missing handler
            // until the i18n JSON is in the store. Doing this in APP_INITIALIZER gates
            // AppComponent rendering on the HTTP load instead of racing against it.
            translateService.setFallbackLang('en');
            const languageKey: string = sessionStorageService.retrieve('locale') || languageHelper.determinePreferredLanguage();
            // The .catch is load-bearing: Promise.all below short-circuits on the first rejection,
            // and a flaky i18n endpoint must degrade gracefully (missing-key placeholders, same as
            // the previous fire-and-forget behavior) rather than block the SPA from booting at all.
            const translationsLoaded = lastValueFrom(translateService.use(languageKey)).catch((error) => {
                // eslint-disable-next-line no-undef
                console.warn('Translation load failed during app initialization', error);
                return undefined;
            });
            // Load profile info, resolve user identity, and fetch translations in parallel to minimize startup time.
            // Profile info is required for all components; identity resolution avoids a sequential HTTP call in route guards.
            return Promise.all([profileService.loadProfileInfo(), completeSaml2.then(() => accountService.identity().catch(() => undefined)), translationsLoaded]).then(
                () => undefined,
            );
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
        { provide: Configuration, useFactory: () => new Configuration({ withCredentials: true, basePath: '' }) },
        providePrimeNG({
            theme: {
                preset: AuraArtemis,
                options: {
                    darkModeSelector: '[prime-ng-use-dark-theme="true"]',
                },
            },
        }),
    ],
};
