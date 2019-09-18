import { DatePipe } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { ElementRef, NgModule, Renderer2 } from '@angular/core';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { NgbActiveModal, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiAlertService, JhiDataUtils, JhiDateUtils, JhiEventManager, JhiLanguageService, JhiParseLinks } from 'ng-jhipster';

import { MockLanguageHelper, MockLanguageService } from './helpers/mock-language.service';
import { AccountService, JhiLanguageHelper } from 'app/core';
import { MockAccountService } from './helpers/mock-account.service';
import { MockActivatedRoute, MockRouter } from './helpers/mock-route.service';
import { MockActiveModal } from './helpers/mock-active-modal.service';
import { MockEventManager } from './helpers/mock-event-manager.service';
import { ArtemisIconsModule } from 'app/shared/icons/icons.module';

// TODO: This module was taken from auto generated tests. Needs to be reworked completely.
@NgModule({
    providers: [
        DatePipe,
        JhiDataUtils,
        JhiDateUtils,
        JhiParseLinks,
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
        // {
        //     provide: Principal,
        //     useClass: MockPrincipal,
        // },
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
    imports: [HttpClientTestingModule, ArtemisIconsModule],
    exports: [ArtemisIconsModule],
})
export class ArtemisTestModule {}
