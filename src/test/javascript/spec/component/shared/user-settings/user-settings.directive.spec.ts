import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AccountService } from 'app/core/auth/account.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import * as sinon from 'sinon';
import * as sinonChai from 'sinon-chai';
import * as chai from 'chai';
import { UserSettingsService } from 'app/shared/user-settings/user-settings.service';
import { SettingId, UserSettingsCategory } from 'app/shared/constants/user-settings.constants';
import { MockWebsocketService } from '../../../helpers/mocks/service/mock-websocket.service';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { TranslateTestingModule } from '../../../helpers/mocks/service/mock-translate.service';
import { NotificationSetting } from 'app/shared/user-settings/notification-settings/notification-settings-structure';
import { ChangeDetectorRef, Component } from '@angular/core';
import { UserSettingsDirective } from 'app/shared/user-settings/user-settings.directive';
import { JhiAlertService } from 'ng-jhipster';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { SinonStub, stub } from 'sinon';
import { MockProvider } from 'ng-mocks';
import { MockUserSettingsService } from '../../../helpers/mocks/service/mock-user-settings.service';

/**
 * needed for testing the abstract UserSettingsDirective
 */
@Component({
    selector: 'jhi-user-settings-mock',
    template: '',
})
class UserSettingsMockComponent extends UserSettingsDirective {
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

describe('User Settings Directive', () => {
    let comp: UserSettingsMockComponent;
    let fixture: ComponentFixture<UserSettingsMockComponent>;

    let userSettingsService: UserSettingsService;
    let httpMock: HttpTestingController;
    let alertService: JhiAlertService;
    let changeDetector: ChangeDetectorRef;

    let changeDetectorDetectChangesStub: SinonStub;

    const router = new MockRouter();

    const notificationSettingA: NotificationSetting = {
        settingId: SettingId.NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_OPEN_FOR_PRACTICE,
        webapp: false,
        email: false,
    };
    const notificationSettingB: NotificationSetting = {
        settingId: SettingId.NOTIFICATION__EXERCISE_NOTIFICATION__NEW_ANSWER_POST_EXERCISES,
        webapp: false,
        email: false,
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule, TranslateTestingModule],
            declarations: [UserSettingsMockComponent],
            providers: [
                MockProvider(ChangeDetectorRef),
                { provide: JhiWebsocketService, useClass: MockWebsocketService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: UserSettingsService, useClass: MockUserSettingsService },
                { provide: Router, useValue: router },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(UserSettingsMockComponent);
                comp = fixture.componentInstance;
                // can be any other category, it does not change the logic
                comp.userSettingsCategory = UserSettingsCategory.NOTIFICATION_SETTINGS;
                userSettingsService = TestBed.inject(UserSettingsService);
                httpMock = TestBed.inject(HttpTestingController);
                comp.alertService = TestBed.inject(JhiAlertService);
                alertService = comp.alertService;
                comp.changeDetector = fixture.debugElement.injector.get(ChangeDetectorRef);
                changeDetector = comp.changeDetector;
                changeDetectorDetectChangesStub = stub(changeDetector.constructor.prototype, 'detectChanges');
            });
    });

    afterEach(() => {
        httpMock.verify();
        sinon.restore();
    });

    describe('Service methods with Category Notification Settings', () => {
        const notificationSettingsForTesting: NotificationSetting[] = [notificationSettingA, notificationSettingB];

        describe('test loadSettings', () => {
            it('should call userSettingsService to load Settings', () => {
                stub(userSettingsService, 'loadSettings').returns(of(new HttpResponse({ body: notificationSettingsForTesting })));
                const loadSettingsSuccessAsSettingsStructureSpy = sinon.spy(userSettingsService, 'loadSettingsSuccessAsSettingsStructure');
                const extractIndividualSettingsFromSettingsStructureSpy = sinon.spy(userSettingsService, 'extractIndividualSettingsFromSettingsStructure');

                comp.ngOnInit();

                expect(loadSettingsSuccessAsSettingsStructureSpy).to.be.calledOnce;
                expect(extractIndividualSettingsFromSettingsStructureSpy).to.be.calledOnce;
                expect(changeDetectorDetectChangesStub).to.have.been.called;
            });

            it('should handle error correctly when loading fails', () => {
                const alertServiceSpy = sinon.spy(comp.alertService, 'error');
                const errorResponse = new HttpErrorResponse({ status: 403 });
                stub(userSettingsService, 'loadSettings').returns(throwError(errorResponse));

                comp.ngOnInit();

                expect(alertServiceSpy).to.be.calledOnce;
            });
        });

        describe('test savingSettings', () => {
            it('should call userSettingsService to save Settings', () => {
                stub(userSettingsService, 'saveSettings').returns(of(new HttpResponse({ body: notificationSettingsForTesting })));
                const saveSettingsSuccessSpy = sinon.spy(userSettingsService, 'saveSettingsSuccess');
                const extractIndividualSettingsFromSettingsStructureSpy = sinon.spy(userSettingsService, 'extractIndividualSettingsFromSettingsStructure');
                const createApplyChangesEventSpy = sinon.spy(userSettingsService, 'sendApplyChangesEvent');
                const alertServiceSuccessSpy = sinon.spy(alertService, 'success');

                comp.saveSettings();

                expect(saveSettingsSuccessSpy).to.be.calledOnce;
                expect(extractIndividualSettingsFromSettingsStructureSpy).to.be.calledOnce;
                expect(createApplyChangesEventSpy).to.be.calledOnce;
                expect(alertServiceSuccessSpy).to.have.been.called;
            });
        });
    });
});
