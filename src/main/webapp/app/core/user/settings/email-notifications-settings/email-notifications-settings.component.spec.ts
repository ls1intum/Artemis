import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { of } from 'rxjs';
import { CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { EmailNotificationsSettingsComponent } from './email-notifications-settings.component';
import { EmailNotificationSettingsService } from './email-notifications-settings.service';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { AlertService } from 'app/shared/service/alert.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';

describe('EmailNotificationsSettingsComponent', () => {
    let component: EmailNotificationsSettingsComponent;
    let fixture: ComponentFixture<EmailNotificationsSettingsComponent>;

    const mockService = {
        getAll: jest.fn(),
        update: jest.fn(),
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FormsModule, EmailNotificationsSettingsComponent],
            declarations: [MockDirective(TranslateDirective), MockPipe(ArtemisDatePipe)],
            providers: [{ provide: EmailNotificationSettingsService, useValue: mockService }, MockProvider(ProfileService), MockProvider(AlertService)],
            schemas: [CUSTOM_ELEMENTS_SCHEMA],
        }).compileComponents();

        fixture = TestBed.createComponent(EmailNotificationsSettingsComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        jest.resetAllMocks();
    });

    const mockSettings = {
        NEW_LOGIN: true,
        NEW_PASSKEY_ADDED: false,
        VCS_TOKEN_EXPIRED: true,
        SSH_KEY_EXPIRED: false,
    };

    it('should load notification settings on init', fakeAsync(() => {
        mockService.getAll.mockReturnValue(of(mockSettings));

        component.ngOnInit();
        tick();

        expect(mockService.getAll).toHaveBeenCalledOnce();
        expect(component.notificationSettings).toEqual(mockSettings);
    }));

    it('should update a notification setting', fakeAsync(() => {
        mockService.update.mockReturnValue(of({}));
        component.notificationSettings = { ...mockSettings };

        component.updateSetting('NEW_LOGIN', false);
        tick();

        expect(mockService.update).toHaveBeenCalledWith('NEW_LOGIN', false);
        expect(component.notificationSettings?.NEW_LOGIN).toBeFalse();
    }));

    it('should generate the correct i18n label key', () => {
        const labelKey = component.getNotificationTypeLabel('SSH_KEY_EXPIRED');
        expect(labelKey).toBe('artemisApp.userSettings.emailNotificationSettings.options.SSH_KEY_EXPIRED');
    });
});
