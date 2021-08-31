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
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { SinonStub, stub } from 'sinon';
import { MockProvider } from 'ng-mocks';

/**
 * needed for testing the abstract UserSettingsPrototypeComponent
 */
class UserSettingsPrototypeComponentMock extends UserSettingsPrototypeComponent {
    changeDetector: ChangeDetectorRef;
    alertService: JhiAlertService;

    constructor(userSettingsService: UserSettingsService, changeDetector: ChangeDetectorRef, alertService: JhiAlertService) {
        super(userSettingsService, alertService, changeDetector);
        this.changeDetector = changeDetector;
        this.alertService = alertService;
    }
}

chai.use(sinonChai);
const expect = chai.expect;

describe('User Settings Prototype Component', () => {
    let comp: UserSettingsPrototypeComponentMock;
    let fixture: ComponentFixture<UserSettingsPrototypeComponentMock>;

    let userSettingsService: UserSettingsService;
    let httpMock: HttpTestingController;
    let alertService: JhiAlertService;
    let changeDetector: ChangeDetectorRef;

    let changeDetectorDetectChangesStub: SinonStub;

    const router = new MockRouter();

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
                MockProvider(ChangeDetectorRef),
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
                comp.alertService = TestBed.inject(JhiAlertService);
                comp.changeDetector = fixture.debugElement.injector.get(ChangeDetectorRef);

                changeDetector = fixture.debugElement.injector.get(ChangeDetectorRef);
                changeDetectorDetectChangesStub = stub(changeDetector.constructor.prototype, 'detectChanges');
                alertService = fixture.debugElement.injector.get(JhiAlertService);
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
                stub(userSettingsService, 'loadUserOptions').returns(of(new HttpResponse({ body: notificationOptionCoresForTesting })));
                const loadUserOptionCoresSuccessAsSettingsSpy = sinon.spy(userSettingsService, 'loadUserOptionCoresSuccessAsSettings');
                const extractOptionCoresFromSettingsSpy = sinon.spy(userSettingsService, 'extractOptionCoresFromSettings');

                comp.ngOnInit();

                expect(loadUserOptionCoresSuccessAsSettingsSpy).to.be.calledOnce;
                expect(extractOptionCoresFromSettingsSpy).to.be.calledOnce;
                expect(changeDetectorDetectChangesStub).to.have.been.called;
            });

            it('should handle error correctly when loading fails', () => {
                const alertServiceSpy = sinon.spy(comp.alertService, 'error');
                const errorResponse = new HttpErrorResponse({ status: 403 });
                stub(userSettingsService, 'loadUserOptions').returns(throwError(errorResponse));

                comp.ngOnInit();

                expect(alertServiceSpy).to.be.calledOnce;
            });
        });

        describe('test savingSettings', () => {
            it('should call userSettingsService to save OptionsCores', () => {
                stub(userSettingsService, 'saveUserOptions').returns(of(new HttpResponse({ body: notificationOptionCoresForTesting })));
                const saveUserOptionCoresSuccessSpy = sinon.spy(userSettingsService, 'saveUserOptionsSuccess');
                const extractOptionCoresFromSettingsSpy = sinon.spy(userSettingsService, 'extractOptionCoresFromSettings');
                const createApplyChangesEventSpy = sinon.spy(userSettingsService, 'sendApplyChangesEvent');
                const alertServiceSuccessSpy = sinon.spy(alertService, 'success');

                comp.saveOptions();

                expect(changeDetectorDetectChangesStub).to.have.been.called;
                expect(saveUserOptionCoresSuccessSpy).to.be.calledOnce;
                expect(extractOptionCoresFromSettingsSpy).to.be.calledOnce;
                expect(createApplyChangesEventSpy).to.be.calledOnce;
                expect(alertServiceSuccessSpy).to.have.been.called;
            });
        });
    });
});
