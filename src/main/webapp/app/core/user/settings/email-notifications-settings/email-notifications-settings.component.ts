import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { faSpinner } from '@fortawesome/free-solid-svg-icons';
import { EmailNotificationSettingsService } from './email-notifications-settings.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { FormsModule } from '@angular/forms';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { FEATURE_PASSKEY } from 'app/app.constants';
import { AlertService } from 'app/shared/service/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { Subscription } from 'rxjs';

@Component({
    selector: 'jhi-email-notifications-settings',
    imports: [TranslateDirective, FaIconComponent, FormsModule],
    templateUrl: './email-notifications-settings.component.html',
    styleUrls: ['../user-settings.scss'],
})
export class EmailNotificationsSettingsComponent implements OnInit, OnDestroy {
    protected readonly faSpinner = faSpinner;
    protected readonly notificationTypes = ['NEW_LOGIN', 'NEW_PASSKEY_ADDED', 'VCS_TOKEN_EXPIRED', 'SSH_KEY_EXPIRED'];
    notificationSettings: { [key: string]: boolean } | null = null;

    private emailNotificationSettingsService = inject(EmailNotificationSettingsService);
    private profileService = inject(ProfileService);
    private alertService = inject(AlertService);

    private getAllSub?: Subscription;
    private updateSub?: Subscription;

    isPasskeyEnabled = false;

    ngOnInit(): void {
        this.isPasskeyEnabled = this.profileService.isModuleFeatureActive(FEATURE_PASSKEY);
        this.loadSettings();
    }

    ngOnDestroy(): void {
        this.getAllSub?.unsubscribe();
        this.updateSub?.unsubscribe();
    }

    loadSettings(): void {
        this.getAllSub = this.emailNotificationSettingsService.getAll().subscribe({
            next: (settings: { [key: string]: boolean } | null) => {
                this.notificationSettings = settings;
            },
            error: (error) => {
                onError(this.alertService, error);
            },
        });
    }

    updateSetting(type: string, enabled: boolean): void {
        this.updateSub = this.emailNotificationSettingsService.update(type, enabled).subscribe({
            next: () => {
                if (this.notificationSettings) {
                    this.notificationSettings[type] = enabled;
                }
            },
            error: (error) => onError(this.alertService, error),
        });
    }

    getNotificationTypeLabel(type: string): string {
        return `artemisApp.userSettings.emailNotificationSettings.options.${type}`;
    }

    isSettingAvailable(type: string): boolean {
        return type !== 'NEW_PASSKEY_ADDED' || this.isPasskeyEnabled;
    }
}
