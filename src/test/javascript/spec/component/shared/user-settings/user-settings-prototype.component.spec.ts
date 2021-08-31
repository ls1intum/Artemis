import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AccountService } from 'app/core/auth/account.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import * as sinon from 'sinon';
import * as sinonChai from 'sinon-chai';
import * as chai from 'chai';
import { UserSettingsService } from 'app/shared/user-settings/user-settings.service';
import { OptionSpecifier, UserSettingsCategory } from 'app/shared/constants/user-settings.constants';
import { MockWebsocketService } from '../../../helpers/mocks/service/mock-websocket.service';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { TranslateTestingModule } from '../../../helpers/mocks/service/mock-translate.service';
import { NotificationOptionCore } from 'app/shared/user-settings/notification-settings/notification-settings.default';
import { ChangeDetectorRef } from '@angular/core';
import { UserSettingsPrototypeComponent } from 'app/shared/user-settings/user-settings-prototype/user-settings-prototype.component';
import { JhiAlertService } from 'ng-jhipster';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { stub } from 'sinon';

/**
 * needed for testing the abstract UserSettingsPrototypeComponent
 */
class UserSettingsPrototypeComponentMock extends UserSettingsPrototypeComponent {
    changeDetector: ChangeDetectorRef;

    constructor(userSettingsService: UserSettingsService, changeDetector: ChangeDetectorRef, alertService: JhiAlertService) {
        super(userSettingsService, alertService, changeDetector);
        this.changeDetector = changeDetector;
    }
}

chai.use(sinonChai);
const expect = chai.expect;

describe('User Settings Prototype Component', () => {
    // general & common
    let comp: UserSettingsPrototypeComponentMock;
    let fixture: ComponentFixture<UserSettingsPrototypeComponentMock>;

    let userSettingsService: UserSettingsService;
    let httpMock: HttpTestingController;
    // let notificationService: NotificationService;
    // let alertService: JhiAlertService;
    let changeDetector: ChangeDetectorRef;

    const router = new MockRouter();

    // notification settings specific
    const notificationOptionCoreA: NotificationOptionCore = {
        id: 1,
        optionSpecifier: OptionSpecifier.NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_OPEN_FOR_PRACTICE,
        webapp: false,
        email: false,
    };
    const notificationOptionCoreB: NotificationOptionCore = {
        id: 2,
        optionSpecifier: OptionSpecifier.NOTIFICATION__EXERCISE_NOTIFICATION__NEW_ANSWER_POST_EXERCISES,
        webapp: false,
        email: false,
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule, TranslateTestingModule],
            declarations: [UserSettingsPrototypeComponentMock],
            providers: [
                //MockProvider(ChangeDetectorRef),
                ChangeDetectorRef,
                { provide: JhiWebsocketService, useClass: MockWebsocketService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: Router, useValue: router },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(UserSettingsPrototypeComponentMock);
                comp = fixture.componentInstance;
                // can be any other category, it does not change the logic
                comp.userSettingsCategory = UserSettingsCategory.NOTIFICATION_SETTINGS;
                userSettingsService = TestBed.inject(UserSettingsService);
                httpMock = TestBed.inject(HttpTestingController);
                // notificationService = TestBed.inject(NotificationService);
                // alertService = TestBed.inject(JhiAlertService);
                comp.changeDetector = fixture.debugElement.injector.get(ChangeDetectorRef);
            });
    });

    afterEach(() => {
        httpMock.verify();
        sinon.restore();
    });

    describe('Service methods with Category Notification Settings', () => {
        const notificationOptionCoresForTesting: NotificationOptionCore[] = [notificationOptionCoreA, notificationOptionCoreB];

        describe('test loadSettings', () => {
            it('should call userSettingsService to load OptionsCores', () => {
                stub(changeDetector.constructor.prototype, 'detectChanges');
                stub(userSettingsService, 'loadUserOptions').returns(of(new HttpResponse({ body: notificationOptionCoresForTesting })));
                const loadUserOptionCoresSuccessAsSettingsSpy = sinon.spy(userSettingsService, 'loadUserOptionCoresSuccessAsSettings');
                const extractOptionCoresFromSettingsSpy = sinon.spy(userSettingsService, 'extractOptionCoresFromSettings');

                comp.ngOnInit();

                expect(loadUserOptionCoresSuccessAsSettingsSpy).to.be.calledOnce;
                expect(extractOptionCoresFromSettingsSpy).to.be.calledOnce;
            });
            /*
            it('should handle error correctly when loading fails',() => {
                // TODO error is registered an onError is called, but again problems with expect on the new spy
                // I tried fixing this for hours  (I already copied several different similar assertions from
                // the artemis code base, but with no success

                const alertServiceSpy = sinon.spy(alertService, 'error');
                const errorResponse = new HttpErrorResponse({ status: 403 });
                stub(userSettingsService, 'loadUserOptions').returns(throwError(errorResponse));

                component.ngOnInit();
                //fixture.detectChanges();

                expect(alertServiceSpy).to.be.calledOnce;
                //expect(alertService.error).to.have.been.called;
            });
 */
        });

        describe('test savingSettings', () => {
            it('should call userSettingsService to save OptionsCores', () => {
                // TODO once again after several hours I can not manage to correctly spy/stub/fake/mock changeDetection without breaking the execution chain
                //stub(changeDetector.constructor.prototype, 'detectChanges');
                //sinon.spy(changeDetector.constructor.prototype, 'detectChanges');

                //const changeDetectorRef = fixture.debugElement.injector.get(ChangeDetectorRef);
                //const detectChangesSpy = spyOn(changeDetectorRef.constructor.prototype, 'detectChanges');

                //const changeDetectorRef = fixture.debugElement.injector.get(ChangeDetectorRef);
                //const detectChangesSpy = spyOn(changeDetectorRef.constructor.prototype, 'detectChanges');

                changeDetector = fixture.debugElement.injector.get(ChangeDetectorRef);
                const changeDetectorDetectChangesStub = stub(changeDetector.constructor.prototype, 'detectChanges');

                stub(userSettingsService, 'saveUserOptions').returns(of(new HttpResponse({ body: notificationOptionCoresForTesting })));
                const saveUserOptionCoresSuccessSpy = sinon.spy(userSettingsService, 'saveUserOptionsSuccess');
                const extractOptionCoresFromSettingsSpy = sinon.spy(userSettingsService, 'extractOptionCoresFromSettings');
                const createApplyChangesEventSpy = sinon.spy(userSettingsService, 'sendApplyChangesEvent');

                //component = fixture.componentInstance;
                //const spy = spyOn((component as any).changeDetector, 'detectChanges');

                //fixture.detectChanges();
                comp.saveOptions();

                expect(changeDetectorDetectChangesStub).to.have.been.called;

                expect(saveUserOptionCoresSuccessSpy).to.be.calledOnce;
                expect(extractOptionCoresFromSettingsSpy).to.be.calledOnce;
                expect(createApplyChangesEventSpy).to.be.calledOnce;
            });
        });
    });
});
