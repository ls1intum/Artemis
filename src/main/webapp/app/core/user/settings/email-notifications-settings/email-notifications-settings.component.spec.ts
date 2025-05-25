import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { EmailNotificationsSettingsComponent } from './email-notifications-settings.component';
import { EmailNotificationSettingsService } from './email-notifications-settings.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { of } from 'rxjs';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ProfileService } from '../../../layouts/profiles/shared/profile.service';

// Mock settings data
const mockSettings = {
    NEW_LOGIN: true,
    NEW_PASSKEY_ADDED: false,
    VCS_TOKEN_EXPIRED: true,
    SSH_KEY_EXPIRED: false,
};

describe('EmailNotificationsSettingsComponent', () => {
    let component: EmailNotificationsSettingsComponent;
    let fixture: ComponentFixture<EmailNotificationsSettingsComponent>;
    let service: jest.Mocked<EmailNotificationSettingsService>;

    beforeEach(async () => {
        const mockService: Partial<EmailNotificationSettingsService> = {
            getAll: jest.fn(),
            update: jest.fn(),
        };

        await TestBed.configureTestingModule({
            imports: [CommonModule, FormsModule, EmailNotificationsSettingsComponent],
            providers: [{ provide: EmailNotificationSettingsService, useValue: mockService }, MockProvider(ProfileService)],
            declarations: [MockDirective(TranslateDirective), MockPipe(ArtemisDatePipe)],
        }).compileComponents();

        fixture = TestBed.createComponent(EmailNotificationsSettingsComponent);
        component = fixture.componentInstance;
        service = TestBed.inject(EmailNotificationSettingsService) as jest.Mocked<EmailNotificationSettingsService>;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create the component', () => {
        expect(component).toBeTruthy();
    });

    it('should load notification settings on init', fakeAsync(() => {
        service.getAll!.mockReturnValue(of(mockSettings));

        component.ngOnInit();
        tick();

        expect(service.getAll).toHaveBeenCalledOnce();
        expect(component.notificationSettings).toEqual(mockSettings);
    }));

    it('should update a notification setting', fakeAsync(() => {
        service.update!.mockReturnValue(of({}));
        component.notificationSettings = { ...mockSettings };

        component.updateSetting('NEW_LOGIN', false);
        tick();

        expect(service.update).toHaveBeenCalledWith('NEW_LOGIN', false);
        expect(component.notificationSettings?.NEW_LOGIN).toBeFalse();
    }));

    it('should generate the correct i18n label key', () => {
        const labelKey = component.getNotificationTypeLabel('SSH_KEY_EXPIRED');
        expect(labelKey).toBe('artemisApp.userSettings.emailNotificationSettings.options.SSH_KEY_EXPIRED');
    });
});
