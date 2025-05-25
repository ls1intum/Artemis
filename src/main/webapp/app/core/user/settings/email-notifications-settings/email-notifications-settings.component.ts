import { faSpinner } from '@fortawesome/free-solid-svg-icons';
import { EmailNotificationSettingsService } from './email-notifications-settings.service';
import { Component, OnInit } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { inject } from '@angular/core';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { FEATURE_PASSKEY } from 'app/app.constants';

@Component({
    selector: 'jhi-email-notifications-settings',
    imports: [TranslateDirective, FaIconComponent, CommonModule, FormsModule],
    templateUrl: './email-notifications-settings.component.html',
    styleUrls: ['../user-settings.scss'],
})
export class EmailNotificationsSettingsComponent implements OnInit {
    faSpinner = faSpinner;
    notificationTypes = ['NEW_LOGIN', 'NEW_PASSKEY_ADDED', 'VCS_TOKEN_EXPIRED', 'SSH_KEY_EXPIRED'];
    notificationSettings: { [key: string]: boolean } | null = null;

    private emailNotificationSettingsService = inject(EmailNotificationSettingsService);
    private profileService = inject(ProfileService);

    isPasskeyEnabled = false;

    ngOnInit(): void {
        this.isPasskeyEnabled = this.profileService.isModuleFeatureActive(FEATURE_PASSKEY);
        this.loadSettings();
    }

    loadSettings(): void {
        this.emailNotificationSettingsService.getAll().subscribe((settings: { [key: string]: boolean } | null) => {
            this.notificationSettings = settings;
        });
    }

    updateSetting(type: string, enabled: boolean): void {
        this.emailNotificationSettingsService.update(type, enabled).subscribe(() => {
            if (this.notificationSettings) {
                this.notificationSettings[type] = enabled;
            }
        });
    }

    getNotificationTypeLabel(type: string): string {
        return `artemisApp.userSettings.emailNotificationSettings.options.${type}`;
    }
}
