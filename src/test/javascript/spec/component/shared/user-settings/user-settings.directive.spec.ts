import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ChangeDetectorRef, Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/core/util/alert.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { SettingId, UserSettingsCategory } from 'app/shared/constants/user-settings.constants';
import { NotificationSetting } from 'app/shared/user-settings/notification-settings/notification-settings-structure';
import { UserSettingsDirective } from 'app/shared/user-settings/user-settings.directive';
import { UserSettingsService } from 'app/shared/user-settings/user-settings.service';
import { MockProvider } from 'ng-mocks';
import { of, throwError } from 'rxjs';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { TranslateTestingModule } from '../../../helpers/mocks/service/mock-translate.service';
import { MockUserSettingsService } from '../../../helpers/mocks/service/mock-user-settings.service';
import { MockWebsocketService } from '../../../helpers/mocks/service/mock-websocket.service';

/**
 * needed for testing the abstract UserSettingsDirective
 */
@Component({
    selector: 'jhi-user-settings-mock',
    template: '',
})
class UserSettingsMockComponent extends UserSettingsDirective {
    changeDetector: ChangeDetectorRef;
    alertService: AlertService;

    constructor(userSettingsService: UserSettingsService, changeDetector: ChangeDetectorRef, alertService: AlertService) {
        super(userSettingsService, alertService, changeDetector);
        this.changeDetector = changeDetector;
        this.alertService = alertService;
    }
}

describe('User Settings Directive', () => {
    let comp: UserSettingsMockComponent;
    let fixture: ComponentFixture<UserSettingsMockComponent>;

    let userSettingsService: UserSettingsService;
    let httpMock: HttpTestingController;
    let alertService: AlertService;
    let changeDetector: ChangeDetectorRef;

    let changeDetectorDetectChangesSpy: jest.SpyInstance;

    const router = new MockRouter();

    const notificationSettingA: NotificationSetting = {
        settingId: SettingId.NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_OPEN_FOR_PRACTICE,
        webapp: false,
        email: false,
    };
    const notificationSettingB: NotificationSetting = {
        settingId: SettingId.NOTIFICATION__EXERCISE_NOTIFICATION__NEW_REPLY_FOR_EXERCISE_POST,
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
                comp.alertService = TestBed.inject(AlertService);
                alertService = comp.alertService;
                comp.changeDetector = fixture.debugElement.injector.get(ChangeDetectorRef);
                changeDetector = comp.changeDetector;
                changeDetectorDetectChangesSpy = jest.spyOn(changeDetector.constructor.prototype, 'detectChanges');
            });
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('Service methods with Category Notification Settings', () => {
        const notificationSettingsForTesting: NotificationSetting[] = [notificationSettingA, notificationSettingB];

        describe('test loadSettings', () => {
            it('should call userSettingsService to load Settings', () => {
                jest.spyOn(userSettingsService, 'loadSettings').mockReturnValue(of(new HttpResponse({ body: notificationSettingsForTesting })));
                const loadSettingsSuccessAsSettingsStructureSpy = jest.spyOn(userSettingsService, 'loadSettingsSuccessAsSettingsStructure');
                const extractIndividualSettingsFromSettingsStructureSpy = jest.spyOn(userSettingsService, 'extractIndividualSettingsFromSettingsStructure');

                comp.ngOnInit();

                expect(loadSettingsSuccessAsSettingsStructureSpy).toHaveBeenCalledOnce();
                expect(extractIndividualSettingsFromSettingsStructureSpy).toHaveBeenCalledOnce();
                expect(changeDetectorDetectChangesSpy).toHaveBeenCalledOnce();
            });

            it('should handle error correctly when loading fails', () => {
                const alertServiceSpy = jest.spyOn(comp.alertService, 'error');
                const errorResponse = new HttpErrorResponse({ status: 403 });
                jest.spyOn(userSettingsService, 'loadSettings').mockReturnValue(throwError(() => errorResponse));

                comp.ngOnInit();

                expect(alertServiceSpy).toHaveBeenCalledOnce();
            });
        });

        describe('test savingSettings', () => {
            it('should call userSettingsService to save Settings', () => {
                jest.spyOn(userSettingsService, 'saveSettings').mockReturnValue(of(new HttpResponse({ body: notificationSettingsForTesting })));
                const saveSettingsSuccessSpy = jest.spyOn(userSettingsService, 'saveSettingsSuccess');
                const extractIndividualSettingsFromSettingsStructureSpy = jest.spyOn(userSettingsService, 'extractIndividualSettingsFromSettingsStructure');
                const createApplyChangesEventSpy = jest.spyOn(userSettingsService, 'sendApplyChangesEvent');
                const alertServiceSuccessSpy = jest.spyOn(alertService, 'success');

                comp.saveSettings();

                expect(saveSettingsSuccessSpy).toHaveBeenCalledOnce();
                expect(extractIndividualSettingsFromSettingsStructureSpy).toHaveBeenCalledOnce();
                expect(createApplyChangesEventSpy).toHaveBeenCalledOnce();
                expect(alertServiceSuccessSpy).toHaveBeenCalledOnce();
            });
        });
    });
});
