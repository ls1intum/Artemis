import { LOCALE_ID, NgModule, Sanitizer, ErrorHandler } from '@angular/core';
import { DatePipe, registerLocaleData } from '@angular/common';
import { HttpClientModule, HTTP_INTERCEPTORS, HttpClient } from '@angular/common/http';
import { Title, BrowserModule } from '@angular/platform-browser';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { SentryErrorHandler } from 'app/sentry/sentry.error-handler';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { RepositoryInterceptor, RepositoryService } from 'app/entities/repository';
import { PaginationConfig } from 'app/blocks/config/uib-pagination.config';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { AuthInterceptor } from 'app/blocks/interceptor/auth.interceptor';
import { AuthExpiredInterceptor } from 'app/blocks/interceptor/auth-expired.interceptor';
import { ErrorHandlerInterceptor } from 'app/blocks/interceptor/errorhandler.interceptor';
import { NotificationInterceptor } from 'app/blocks/interceptor/notification.interceptor';
import {
    JhiLanguageService,
    JhiConfigService,
    JhiResolvePagingParams,
    JhiAlertService,
    JhiPaginationUtil,
    JhiModuleConfig,
    NgJhipsterModule,
    translatePartialLoader,
    missingTranslationHandler,
} from 'ng-jhipster';
import { TranslateService, TranslateModule, TranslateLoader, MissingTranslationHandler } from '@ngx-translate/core';
import { NgbDatepickerConfig, NgbDateAdapter } from '@ng-bootstrap/ng-bootstrap';
import * as moment from 'moment';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { NgxWebstorageModule } from 'ngx-webstorage';
import { MomentModule, DifferencePipe } from 'ngx-moment';
import { DeviceDetectorModule } from 'ngx-device-detector';
import { Angulartics2Module } from 'angulartics2';
import { NgbDateMomentAdapter } from 'app/shared/util/datepicker-adapter';
import { FaIconLibrary } from '@fortawesome/angular-fontawesome';
import { fas } from '@fortawesome/free-solid-svg-icons';
import locale from '@angular/common/locales/en';
import { fontAwesomeIcons } from 'app/core/icons/font-awesome-icons';
import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';
import { ProfileService } from 'app/layouts/profiles/profile.service';

@NgModule({
    imports: [
        HttpClientModule,
        BrowserModule,
        BrowserAnimationsModule,
        NgxWebstorageModule.forRoot({ prefix: 'jhi', separator: '-' }),
        DeviceDetectorModule,
        /**
         * @external Moment is a date library for parsing, validating, manipulating, and formatting dates.
         */
        MomentModule,
        NgJhipsterModule.forRoot({
            // set below to true to make alerts look like toast
            alertAsToast: false,
            alertTimeout: 8000,
            i18nEnabled: true,
            defaultI18nLang: 'en',
        }),
        /**
         * @external Angulartics offers Vendor-agnostic analytics and integration with Matomo
         */
        Angulartics2Module.forRoot(),
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
        {
            provide: ErrorHandler,
            useClass: SentryErrorHandler,
        },
        DatePipe,
        JhiWebsocketService,
        GuidedTourService,
        ProfileService,
        RepositoryService,
        PaginationConfig,
        UserRouteAccessService,
        DifferencePipe,
        PendingChangesGuard,
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
            useClass: NotificationInterceptor,
            multi: true,
        },
        {
            provide: HTTP_INTERCEPTORS,
            useClass: RepositoryInterceptor,
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
