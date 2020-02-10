import { DatePipe } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { ElementRef, NgModule, Renderer2 } from '@angular/core';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { NgbActiveModal, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiAlertService, JhiDataUtils, JhiDateUtils, JhiEventManager, JhiLanguageService, JhiParseLinks } from 'ng-jhipster';

import { MockLanguageHelper, MockLanguageService } from './helpers/mock-language.service';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from './helpers/mock-account.service';
import { MockActivatedRoute, MockRouter } from './helpers/mock-route.service';
import { MockActiveModal } from './helpers/mock-active-modal.service';
import { MockEventManager } from './helpers/mock-event-manager.service';
import { CookieModule, CookieOptionsProvider, CookieService } from 'ngx-cookie';
import { MockComponent } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

// TODO: This module was taken from auto generated tests. Needs to be reworked completely.
@NgModule({
    imports: [CookieModule.forRoot(), HttpClientTestingModule],
    providers: [
        DatePipe,
        JhiDataUtils,
        JhiDateUtils,
        JhiParseLinks,
        CookieService,
        CookieOptionsProvider,
        {
            provide: JhiLanguageService,
            useClass: MockLanguageService,
        },
        {
            provide: JhiLanguageHelper,
            useClass: MockLanguageHelper,
        },
        // {
        //     provide: JhiTrackerService,
        //     useValue: null,
        // },
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
            provide: ElementRef,
            useValue: null,
        },
        {
            provide: Renderer2,
            useValue: null,
        },
        {
            provide: JhiAlertService,
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
export class ArtemisTestModule {}
