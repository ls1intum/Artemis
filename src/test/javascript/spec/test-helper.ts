import { TestBed } from '@angular/core/testing';
import { DatePipe, registerLocaleData } from '@angular/common';
import { ElementRef, Renderer2, Provider, EnvironmentProviders } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { NgbActiveModal, NgbDatepickerConfig, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MockProvider } from 'ng-mocks';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import locale from '@angular/common/locales/en';
import dayjs from 'dayjs/esm';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { ParseLinks } from 'app/core/util/parse-links.service';
import { EventManager } from 'app/core/util/event-manager.service';
import { AlertService } from 'app/core/util/alert.service';
import { MockActivatedRoute } from './helpers/mocks/activated-route/mock-activated-route';
import { MockRouter } from './helpers/mocks/mock-router';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from './helpers/mocks/service/mock-account.service';
import { MockTranslateService } from './helpers/mocks/service/mock-translate.service';
import { ThemeService } from 'app/core/theme/theme.service';
import { MockThemeService } from './helpers/mocks/service/mock-theme.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { MockProfileService } from './helpers/mocks/service/mock-profile.service';
import { TranslateService } from '@ngx-translate/core';

export function getArtemisTestProviders(): (Provider | EnvironmentProviders)[] {
    return [
        DatePipe,
        ParseLinks,
        MockProvider(EventManager),
        MockProvider(NgbActiveModal),
        MockProvider(AlertService),
        {
            provide: ActivatedRoute,
            useValue: new MockActivatedRoute({ id: 123 }),
        },
        {
            provide: Router,
            useClass: MockRouter,
        },
        {
            provide: AccountService,
            useClass: MockAccountService,
        },
        {
            provide: TranslateService,
            useClass: MockTranslateService,
        },
        {
            provide: ElementRef,
            useValue: null,
        },
        {
            provide: Renderer2,
            useValue: null,
        },
        {
            provide: NgbModal,
            useValue: null,
        },
        {
            provide: ThemeService,
            useClass: MockThemeService,
        },
        {
            provide: ProfileService,
            useClass: MockProfileService,
        },
        provideHttpClient(),
        provideHttpClientTesting(),
    ];
}

export function getArtemisTestImports() {
    return [FontAwesomeTestingModule];
}

export function initializeArtemisTest(): void {
    const dpConfig = TestBed.inject(NgbDatepickerConfig);
    dpConfig.minDate = { year: dayjs().year() - 100, month: 1, day: 1 };

    registerLocaleData(locale);
    const translateService = TestBed.inject(TranslateService);
    translateService.setDefaultLang('en');
}
