import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
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
import { OptionCore, UserSettings } from 'app/shared/user-settings/user-settings.model';
import { defaultNotificationSettings, NotificationOptionCore } from 'app/shared/user-settings/notification-settings/notification-settings.default';
import { NotificationService } from 'app/shared/notification/notification.service';
import { ChangeDetectorRef } from '@angular/core';
import { UserSettingsPrototypeComponent } from 'app/shared/user-settings/user-settings-prototype/user-settings-prototype.component';
import { JhiAlertService } from 'ng-jhipster';
import { MockProvider } from 'ng-mocks';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { Router } from '@angular/router';

/**
 * needed for testing the abstract UserSettingsPrototypeComponent
 */
class UserSettingsPrototypeComponentMock extends UserSettingsPrototypeComponent {
    constructor(userSettingsService: UserSettingsService, changeDetector: ChangeDetectorRef, alertService: JhiAlertService) {
        super(userSettingsService, alertService, changeDetector);
    }
}

chai.use(sinonChai);
const expect = chai.expect;

describe('User Settings Prototype Component', () => {
    // general & common
    let component: UserSettingsPrototypeComponentMock;
    let fixture: ComponentFixture<UserSettingsPrototypeComponentMock>;

    let userSettingsService: UserSettingsService;
    let httpMock: HttpTestingController;
    let userSettingsCategory: UserSettingsCategory;
    let resultingUserSettings: UserSettings<OptionCore>;
    let notificationService: NotificationService;
    let alertService: JhiAlertService;
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
            //declarations: [UserSettingsPrototypeComponent],
            providers: [
                MockProvider(JhiAlertService),
                //MockProvider(UserSettingsPrototypeComponentMock),
                MockProvider(ChangeDetectorRef),
                { provide: JhiWebsocketService, useClass: MockWebsocketService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: Router, useValue: router },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(UserSettingsPrototypeComponentMock);
                component = fixture.componentInstance;
                //component = TestBed.inject(UserSettingsPrototypeComponent);
                userSettingsService = TestBed.inject(UserSettingsService);
                httpMock = TestBed.inject(HttpTestingController);
                notificationService = TestBed.inject(NotificationService);
                alertService = TestBed.inject(JhiAlertService);
                changeDetector = TestBed.inject(ChangeDetectorRef);
            });
    });

    afterEach(() => {
        httpMock.verify();
        sinon.restore();
    });

    // because this component should be looked at
    describe('Service methods with Category Notification Settings', () => {
        userSettingsCategory = UserSettingsCategory.NOTIFICATION_SETTINGS;
        const notificationOptionCoresForTesting: NotificationOptionCore[] = [notificationOptionCoreA, notificationOptionCoreB];

        describe('test loadSettings', () => {
            it('should call userSettingsService to load OptionsCores', () => {
                /*
                const loadUserOptionsSpy = sinon.spy(userSettingsService, 'loadUserOptions');
                const loadUserOptionCoresSuccessAsSettingsSpy = sinon.spy(userSettingsService, 'loadUserOptionCoresSuccessAsSettings');
                const extractOptionCoresFromSettings = sinon.spy(userSettingsService, 'extractOptionCoresFromSettings');

                component.ngOnInit();
                fixture.detectChanges();

                expect(loadUserOptionsSpy).to.be.calledOnce;
                expect(loadUserOptionCoresSuccessAsSettingsSpy).to.be.calledOnce;
                expect(extractOptionCoresFromSettings).to.be.calledOnce;
                 */
            });
        });
    });
});
