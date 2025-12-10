import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AccountService } from 'app/core/auth/account.service';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { UserSettingsCategory } from 'app/shared/constants/user-settings.constants';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { Component } from '@angular/core';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { MockProvider } from 'ng-mocks';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { UserSettingsDirective } from 'app/core/user/settings/directive/user-settings.directive';
import { UserSettingsService } from 'app/core/user/settings/directive/user-settings.service';
import { HttpResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { Setting, UserSettingsStructure } from 'app/core/user/settings/user-settings.model';
import { AlertService } from 'app/shared/service/alert.service';

/**
 * needed for testing the abstract UserSettingsDirective
 */
@Component({
    selector: 'jhi-user-settings-mock',
    template: '',
})
class UserSettingsMockComponent extends UserSettingsDirective {}

describe('User Settings Directive', () => {
    let comp: UserSettingsMockComponent;
    let fixture: ComponentFixture<UserSettingsMockComponent>;

    let userSettingsService: UserSettingsService;
    let alertService: AlertService;

    const router = new MockRouter();

    const mockSettings: Setting[] = [
        // @ts-ignore
        { id: 1, settingKey: 'key1', settingValue: 'value1', user: { id: 1 } },
        // @ts-ignore
        { id: 2, settingKey: 'key2', settingValue: 'value2', user: { id: 1 } },
    ];

    const mockUserSettingsStructure: UserSettingsStructure<Setting> = {
        // @ts-ignore
        key1: mockSettings[0],
        key2: mockSettings[1],
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [UserSettingsMockComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(UserSettingsService),
                MockProvider(AlertService),
                { provide: WebsocketService, useClass: MockWebsocketService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: Router, useValue: router },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(UserSettingsMockComponent);
        comp = fixture.componentInstance;
        comp.userSettingsCategory = UserSettingsCategory.SCIENCE_SETTINGS;
        comp.changeEventMessage = 'settings.changed';

        userSettingsService = TestBed.inject(UserSettingsService);
        alertService = TestBed.inject(AlertService);

        jest.spyOn(alertService, 'closeAll');
        jest.spyOn(alertService, 'success');
        jest.spyOn(alertService, 'error');
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    describe('ngOnInit', () => {
        it('should close all alerts and load settings', () => {
            jest.spyOn(comp, 'loadSetting' as any);

            comp.ngOnInit();

            expect(alertService.closeAll).toHaveBeenCalled();
            expect(comp['loadSetting']).toHaveBeenCalled();
        });
    });

    describe('loadSetting', () => {
        it('should load settings successfully', () => {
            const httpResponse = new HttpResponse<Setting[]>({ body: mockSettings });
            jest.spyOn(userSettingsService, 'loadSettings').mockReturnValue(of(httpResponse));
            jest.spyOn(userSettingsService, 'loadSettingsSuccessAsSettingsStructure').mockReturnValue(mockUserSettingsStructure);
            jest.spyOn(userSettingsService, 'extractIndividualSettingsFromSettingsStructure').mockReturnValue(mockSettings);

            comp['loadSetting']();

            expect(userSettingsService.loadSettings).toHaveBeenCalledWith(UserSettingsCategory.SCIENCE_SETTINGS);
            expect(userSettingsService.loadSettingsSuccessAsSettingsStructure).toHaveBeenCalledWith(mockSettings, UserSettingsCategory.SCIENCE_SETTINGS);
            expect(userSettingsService.extractIndividualSettingsFromSettingsStructure).toHaveBeenCalledWith(mockUserSettingsStructure);
            expect(alertService.closeAll).toHaveBeenCalled();

            expect(comp.userSettings).toEqual(mockUserSettingsStructure);
            expect(comp.settings).toEqual(mockSettings);
        });

        it('should handle error when loading settings', () => {
            const errorResponse = { status: 404, statusText: 'Not Found', error: { message: 'Settings not found', params: {} } };
            jest.spyOn(userSettingsService, 'loadSettings').mockReturnValue(throwError(() => errorResponse));
            jest.spyOn(comp, 'onError' as any);

            comp['loadSetting']();

            expect(comp['onError']).toHaveBeenCalledWith(errorResponse);
        });
    });

    describe('saveSettings', () => {
        beforeEach(() => {
            comp.settings = mockSettings;
            comp.userSettings = mockUserSettingsStructure;
            comp.userSettingsCategory = UserSettingsCategory.SCIENCE_SETTINGS;
        });

        it('should save settings successfully', () => {
            const httpResponse = new HttpResponse<Setting[]>({ body: mockSettings });
            jest.spyOn(userSettingsService, 'saveSettings').mockReturnValue(of(httpResponse));
            jest.spyOn(userSettingsService, 'saveSettingsSuccess').mockReturnValue(mockUserSettingsStructure);
            jest.spyOn(userSettingsService, 'extractIndividualSettingsFromSettingsStructure').mockReturnValue(mockSettings);
            jest.spyOn(comp, 'finishSaving' as any);

            comp.saveSettings();

            expect(userSettingsService.saveSettings).toHaveBeenCalledWith(mockSettings, UserSettingsCategory.SCIENCE_SETTINGS);
            expect(userSettingsService.saveSettingsSuccess).toHaveBeenCalledWith(mockUserSettingsStructure, mockSettings);
            expect(userSettingsService.extractIndividualSettingsFromSettingsStructure).toHaveBeenCalledWith(mockUserSettingsStructure);
            expect(comp['finishSaving']).toHaveBeenCalled();

            expect(comp.userSettings).toEqual(mockUserSettingsStructure);
            expect(comp.settings).toEqual(mockSettings);
        });

        it('should handle error when saving settings', () => {
            const errorResponse = { status: 500, statusText: 'Server Error', error: { message: 'Failed to save settings', params: {} } };
            jest.spyOn(userSettingsService, 'saveSettings').mockReturnValue(throwError(() => errorResponse));
            jest.spyOn(comp, 'onError' as any);

            comp.saveSettings();

            expect(comp['onError']).toHaveBeenCalledWith(errorResponse);
        });
    });

    describe('finishSaving', () => {
        it('should finalize the saving process', () => {
            jest.spyOn(comp, 'createApplyChangesEvent' as any);
            comp.settingsChanged = true;

            comp['finishSaving']();

            expect(comp['createApplyChangesEvent']).toHaveBeenCalled();
            expect(alertService.closeAll).toHaveBeenCalled();
            expect(alertService.success).toHaveBeenCalledWith('artemisApp.userSettings.saveSettingsSuccessAlert');

            expect(comp.settingsChanged).toBeFalse();
        });
    });

    describe('createApplyChangesEvent', () => {
        it('should send apply changes event with the correct message', () => {
            jest.spyOn(userSettingsService, 'sendApplyChangesEvent');
            comp.changeEventMessage = 'settings.changed';

            comp['createApplyChangesEvent']();

            expect(userSettingsService.sendApplyChangesEvent).toHaveBeenCalledWith('settings.changed');
        });
    });

    describe('onError', () => {
        it('should display error message from the response', () => {
            // Setup error response
            const errorResponse = {
                status: 400,
                statusText: 'Bad Request',
                error: {
                    message: 'validation.error',
                    params: { field: 'key' },
                },
            };

            // @ts-ignore
            comp['onError'](errorResponse);

            expect(alertService.error).toHaveBeenCalledWith('validation.error', { field: 'key' });
        });

        it('should display generic error message when response has no error details', () => {
            const errorResponse = {
                status: 500,
                statusText: 'Server Error',
                message: 'Internal Server Error',
                error: null,
            };

            // @ts-ignore
            comp['onError'](errorResponse);

            expect(alertService.error).toHaveBeenCalledWith('error.unexpectedError', {
                error: 'Internal Server Error',
            });
        });
    });

    describe('Integration', () => {
        it('should update settings and UI after successful save', () => {
            comp.settings = mockSettings;
            comp.userSettings = mockUserSettingsStructure;
            comp.settingsChanged = true;
            comp.userSettingsCategory = UserSettingsCategory.SCIENCE_SETTINGS;
            comp.changeEventMessage = 'settings.changed';

            const updatedSettings = [...mockSettings, { id: 3, settingKey: 'key3', settingValue: 'value3', user: { id: 1 } }];
            const updatedUserSettingsStructure = Object.assign({}, mockUserSettingsStructure, { key3: updatedSettings[2] });

            // @ts-ignore
            const httpResponse = new HttpResponse<Setting[]>({ body: updatedSettings });
            jest.spyOn(userSettingsService, 'saveSettings').mockReturnValue(of(httpResponse));
            jest.spyOn(userSettingsService, 'saveSettingsSuccess').mockReturnValue(updatedUserSettingsStructure);
            // @ts-ignore
            jest.spyOn(userSettingsService, 'extractIndividualSettingsFromSettingsStructure').mockReturnValue(updatedSettings);
            jest.spyOn(userSettingsService, 'sendApplyChangesEvent');

            comp.saveSettings();

            expect(comp.userSettings).toEqual(updatedUserSettingsStructure);
            expect(comp.settings).toEqual(updatedSettings);
            expect(comp.settingsChanged).toBeFalse();
            expect(userSettingsService.sendApplyChangesEvent).toHaveBeenCalledWith('settings.changed');
            expect(alertService.success).toHaveBeenCalledWith('artemisApp.userSettings.saveSettingsSuccessAlert');
        });
    });
});
