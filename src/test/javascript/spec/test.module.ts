import { DatePipe, registerLocaleData } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { ElementRef, NgModule, Renderer2 } from '@angular/core';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { NgbActiveModal, NgbModal, NgbDatepickerConfig } from '@ng-bootstrap/ng-bootstrap';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from './helpers/mocks/service/mock-account.service';
import { MockActivatedRoute } from './helpers/mocks/activated-route/mock-activated-route';
import { MockRouter } from './helpers/mocks/mock-router';
import { MockActiveModal } from './helpers/mocks/service/mock-active-modal.service';
import { MockEventManager } from './helpers/mocks/service/mock-event-manager.service';
import { CookieService } from 'ngx-cookie-service';
import { FaIconLibrary, FontAwesomeModule, FaIconComponent } from '@fortawesome/angular-fontawesome';
import { fas } from '@fortawesome/free-solid-svg-icons';
import locale from '@angular/common/locales/en';
import { fontAwesomeIcons } from 'app/core/icons/font-awesome-icons';
import dayjs from 'dayjs';
import { MockComponent } from 'ng-mocks';
import { MockAlertService } from './helpers/mocks/service/mock-alert.service';
import { EventManager } from 'app/core/util/event-manager.service';
import { AlertService } from 'app/core/util/alert.service';
import { TranslateService } from '@ngx-translate/core';
import { ParseLinks } from 'app/core/util/parse-links.service';
import { MockTranslateService } from './helpers/mocks/service/mock-translate.service';

@NgModule({
    imports: [HttpClientTestingModule],
    providers: [
        DatePipe,
        CookieService,
        ParseLinks,
        {
            provide: EventManager,
            useClass: MockEventManager,
        },
        {
            provide: NgbActiveModal,
            useClass: MockActiveModal,
        },
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
            provide: AlertService,
            useClass: MockAlertService,
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
    ],
    declarations: [MockComponent(FaIconComponent)],
    exports: [MockComponent(FaIconComponent)],
})
export class ArtemisTestModule {
    constructor(iconLibrary: FaIconLibrary, dpConfig: NgbDatepickerConfig, translateService: TranslateService) {
        registerLocaleData(locale);
        iconLibrary.addIconPacks(fas);
        iconLibrary.addIcons(...fontAwesomeIcons);
        dpConfig.minDate = { year: dayjs().year() - 100, month: 1, day: 1 };
        translateService.setDefaultLang('en');
    }
}
