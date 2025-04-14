import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AccountService } from 'app/core/auth/account.service';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { SettingId, UserSettingsCategory } from 'app/shared/constants/user-settings.constants';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ChangeDetectorRef, Component } from '@angular/core';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { MockProvider } from 'ng-mocks';
import { MockUserSettingsService } from 'test/helpers/mocks/service/mock-user-settings.service';
import { AlertService } from 'app/shared/service/alert.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { UserSettingsDirective } from 'app/core/user/settings/directive/user-settings.directive';
import { UserSettingsService } from 'app/core/user/settings/directive/user-settings.service';
import { NotificationSetting } from 'app/core/user/settings/notification-settings/notification-settings-structure';

/**
 * needed for testing the abstract UserSettingsDirective
 */
@Component({
    selector: 'jhi-user-settings-mock',
    template: '',
})
class UserSettingsMockComponent extends UserSettingsDirective {}

describe('User Settings Directive', () => {
    let alertService: AlertService;
    let comp: UserSettingsMockComponent;
    let fixture: ComponentFixture<UserSettingsMockComponent>;

    let userSettingsService: UserSettingsService;
    let changeDetector: ChangeDetectorRef;

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
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(ChangeDetectorRef),
                { provide: WebsocketService, useClass: MockWebsocketService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: UserSettingsService, useClass: MockUserSettingsService },
                { provide: Router, useValue: router },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(UserSettingsMockComponent);
                comp = fixture.componentInstance;
                // can be any other category, it does not change the logic
                comp.userSettingsCategory = UserSettingsCategory.NOTIFICATION_SETTINGS;
                userSettingsService = TestBed.inject(UserSettingsService);
                alertService = TestBed.inject(AlertService);
                changeDetector = fixture.debugElement.injector.get(ChangeDetectorRef);
            });
    });

    describe('Service methods with Category Notification Settings', () => {
        const notificationSettingsForTesting: NotificationSetting[] = [notificationSettingA, notificationSettingB];

        describe('test loadSettings', () => {
            it('should call userSettingsService to load Settings', () => {
                jest.spyOn(userSettingsService, 'loadSettings').mockReturnValue(of(new HttpResponse({ body: notificationSettingsForTesting })));
                const loadSettingsSuccessAsSettingsStructureSpy = jest.spyOn(userSettingsService, 'loadSettingsSuccessAsSettingsStructure');
                const extractIndividualSettingsFromSettingsStructureSpy = jest.spyOn(userSettingsService, 'extractIndividualSettingsFromSettingsStructure');
                const changeDetectorDetectChangesSpy = jest.spyOn(changeDetector.constructor.prototype, 'detectChanges');

                comp.ngOnInit();

                expect(loadSettingsSuccessAsSettingsStructureSpy).toHaveBeenCalledOnce();
                expect(extractIndividualSettingsFromSettingsStructureSpy).toHaveBeenCalledOnce();
                expect(changeDetectorDetectChangesSpy).toHaveBeenCalledOnce();
            });

            it('should handle error correctly when loading fails', () => {
                const alertServiceSpy = jest.spyOn(alertService, 'error');
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
