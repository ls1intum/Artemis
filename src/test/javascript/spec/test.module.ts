import { DatePipe, registerLocaleData } from '@angular/common';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import locale from '@angular/common/locales/en';
import { ElementRef, NgModule, Renderer2 } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FaIconComponent, FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { NgbActiveModal, NgbDatepickerConfig, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { ThemeService } from 'app/core/theme/theme.service';
import { AlertService } from 'app/core/util/alert.service';
import { EventManager } from 'app/core/util/event-manager.service';
import { ParseLinks } from 'app/core/util/parse-links.service';
import dayjs from 'dayjs/esm';
import { MockComponent, MockProvider } from 'ng-mocks';
import { MockActivatedRoute } from './helpers/mocks/activated-route/mock-activated-route';
import { MockRouter } from './helpers/mocks/mock-router';
import { MockAccountService } from './helpers/mocks/service/mock-account.service';
import { MockThemeService } from './helpers/mocks/service/mock-theme.service';
import { MockTranslateService } from './helpers/mocks/service/mock-translate.service';

@NgModule({
    imports: [HttpClientTestingModule, FontAwesomeModule],
    providers: [
        DatePipe,
        ParseLinks,
        MockProvider(EventManager),
        MockProvider(NgbActiveModal),
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
        MockProvider(AlertService),
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
    ],
    declarations: [MockComponent(FaIconComponent)],
    exports: [MockComponent(FaIconComponent)],
})
export class ArtemisTestModule {
    constructor(dpConfig: NgbDatepickerConfig, translateService: TranslateService) {
        registerLocaleData(locale);
        dpConfig.minDate = { year: dayjs().year() - 100, month: 1, day: 1 };
        translateService.setDefaultLang('en');
    }
}
