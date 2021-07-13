import { DatePipe, registerLocaleData } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { ElementRef, NgModule, Renderer2 } from '@angular/core';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { NgbActiveModal, NgbModal, NgbDatepickerConfig } from '@ng-bootstrap/ng-bootstrap';
import { JhiAlertService, JhiDataUtils, JhiDateUtils, JhiEventManager, JhiLanguageService, JhiParseLinks } from 'ng-jhipster';

import { MockLanguageHelper, MockLanguageService } from './helpers/mocks/service/mock-language.service';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
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
import * as moment from 'moment';
import { MockComponent } from 'ng-mocks';
import { MockAlertService } from './helpers/mocks/service/mock-alert.service';
import { MockSyncStorage } from './helpers/mocks/service/mock-sync-storage.service';

@NgModule({
    imports: [HttpClientTestingModule, FontAwesomeModule],
    providers: [
        DatePipe,
        JhiDataUtils,
        JhiDateUtils,
        JhiParseLinks,
        CookieService,
        {
            provide: JhiLanguageService,
            useClass: MockLanguageService,
        },
        {
            provide: JhiLanguageHelper,
            useClass: MockLanguageHelper,
        },
        {
            provide: JhiEventManager,
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
            provide: JhiAlertService,
            useClass: MockAlertService,
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
        { provide: LocalStorageService, useClass: MockSyncStorage },
        { provide: SessionStorageService, useClass: MockSyncStorage },
    ],
    declarations: [MockComponent(FaIconComponent)],
    exports: [MockComponent(FaIconComponent)],
})
export class ArtemisTestModule {
    constructor(iconLibrary: FaIconLibrary, dpConfig: NgbDatepickerConfig, languageService: JhiLanguageService) {
        registerLocaleData(locale);
        iconLibrary.addIconPacks(fas);
        iconLibrary.addIcons(...fontAwesomeIcons);
        dpConfig.minDate = { year: moment().year() - 100, month: 1, day: 1 };
        languageService.init();
    }
}
