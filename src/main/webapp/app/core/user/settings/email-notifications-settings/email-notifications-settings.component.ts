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
import { AlertService } from 'app/shared/service/alert.service';
import { onError } from 'app/shared/util/global.utils';

@Component({
    selector: 'jhi-email-notifications-settings',
    imports: [TranslateDirective, FaIconComponent, CommonModule, FormsModule],
    templateUrl: './email-notifications-settings.component.html',
    styleUrls: ['../user-settings.scss'],
})
export class EmailNotificationsSettingsComponent implements OnInit {
    protected readonly faSpinner = faSpinner;
 protected readonly   notificationTypes = ['NEW_LOGIN', 'NEW_PASSKEY_ADDED', 'VCS_TOKEN_EXPIRED', 'SSH_KEY_EXPIRED'];
    notificationSettings: { [key: string]: boolean } | null = null;

    private emailNotificationSettingsService = inject(EmailNotificationSettingsService);
    private profileService = inject(ProfileService);
    private alertService = inject(AlertService);

    isPasskeyEnabled = false;

    ngOnInit(): void {
        this.isPasskeyEnabled = this.profileService.isModuleFeatureActive(FEATURE_PASSKEY);
        this.loadSettings();
    }

    loadSettings(): void {
        this.emailNotificationSettingsService.getAll().subscribe({
            next: (settings: { [key: string]: boolean } | null) => {
                this.notificationSettings = settings;
            },
            error: (error) => {
                onError(this.alertService, error);
            },
        });
    }

    updateSetting(type: string, enabled: boolean): void {
        this.emailNotificationSettingsService.update(type, enabled).subscribe({
            next: () => {
                if (this.notificationSettings) {
                    this.notificationSettings[type] = enabled;
                }
            },
            error: (error) => {
                onError(this.alertService, error);
            },
        });
    }

    getNotificationTypeLabel(type: string): string {
        return `artemisApp.userSettings.emailNotificationSettings.options.${type}`;
    }
}
