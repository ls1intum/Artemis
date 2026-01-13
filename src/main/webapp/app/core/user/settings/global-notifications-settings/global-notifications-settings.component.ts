import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { faSpinner } from '@fortawesome/free-solid-svg-icons';
import { GlobalNotificationSettingsService } from './global-notifications-settings.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { FormsModule } from '@angular/forms';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_PASSKEY } from 'app/app.constants';
import { AlertService } from 'app/shared/service/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { Subscription } from 'rxjs';
import { RouterLink } from '@angular/router';
import { UserSettingsTitleBarTitleDirective } from 'app/core/user/settings/shared/user-settings-title-bar-title.directive';

export const GLOBAL_NOTIFICATION_TYPES = {
    NEW_LOGIN: 'NEW_LOGIN',
    NEW_PASSKEY_ADDED: 'NEW_PASSKEY_ADDED',
    VCS_TOKEN_EXPIRED: 'VCS_TOKEN_EXPIRED',
    SSH_KEY_EXPIRED: 'SSH_KEY_EXPIRED',
} as const;

export type GlobalNotificationType = keyof typeof GLOBAL_NOTIFICATION_TYPES;

interface NotificationTypeLink {
    type: GlobalNotificationType;
    routerLink: string[];
    translationKey: string;
}

@Component({
    selector: 'jhi-email-notifications-settings',
    imports: [TranslateDirective, FaIconComponent, FormsModule, RouterLink, UserSettingsTitleBarTitleDirective],
    templateUrl: './global-notifications-settings.component.html',
    styleUrls: ['../user-settings.scss'],
})
export class GlobalNotificationsSettingsComponent implements OnInit, OnDestroy {
    protected readonly faSpinner = faSpinner;
    protected readonly notificationTypes = Object.values(GLOBAL_NOTIFICATION_TYPES);
    protected filteredNotificationTypes: GlobalNotificationType[] = [];
    protected notificationLabels: Partial<Record<GlobalNotificationType, string>> = {};

    public readonly notificationTypeLinks: NotificationTypeLink[] = [
        {
            type: GLOBAL_NOTIFICATION_TYPES.NEW_PASSKEY_ADDED,
            routerLink: ['/user-settings', 'passkeys'],
            translationKey: 'artemisApp.userSettings.globalNotificationSettings.viewPasskeySettings',
        },
        {
            type: GLOBAL_NOTIFICATION_TYPES.VCS_TOKEN_EXPIRED,
            routerLink: ['/user-settings', 'vcs-token'],
            translationKey: 'artemisApp.userSettings.globalNotificationSettings.viewVcsTokenSettings',
        },
        {
            type: GLOBAL_NOTIFICATION_TYPES.SSH_KEY_EXPIRED,
            routerLink: ['/user-settings', 'ssh'],
            translationKey: 'artemisApp.userSettings.globalNotificationSettings.viewSshKeySettings',
        },
    ];
    notificationSettings: { [key: string]: boolean } | undefined;

    private globalNotificationSettingsService = inject(GlobalNotificationSettingsService);
    private profileService = inject(ProfileService);
    private alertService = inject(AlertService);

    private getAllSub?: Subscription;
    private updateSub?: Subscription;

    isPasskeyEnabled = false;

    ngOnInit(): void {
        this.isPasskeyEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_PASSKEY);
        this.filteredNotificationTypes = this.notificationTypes.filter((type) => this.isSettingAvailable(type));
        this.notificationLabels = Object.fromEntries(this.filteredNotificationTypes.map((type) => [type, this.getNotificationTypeLabel(type)]));
        this.loadSettings();
    }

    ngOnDestroy(): void {
        this.getAllSub?.unsubscribe();
        this.updateSub?.unsubscribe();
    }

    /**
     * Loads all notification settings for the current user
     */
    loadSettings(): void {
        this.getAllSub?.unsubscribe();
        this.getAllSub = this.globalNotificationSettingsService.getAll().subscribe({
            next: (settings: { [key: string]: boolean } | undefined) => {
                this.notificationSettings = settings;
            },
            error: (error) => {
                onError(this.alertService, error);
            },
        });
    }

    /**
     * Updates a specific notification setting
     * @param type - The notification type to update
     * @param enabled - Whether the notification should be enabled
     */
    updateSetting(type: GlobalNotificationType, enabled: boolean): void {
        this.updateSub?.unsubscribe();
        this.updateSub = this.globalNotificationSettingsService.update(type, enabled).subscribe({
            next: () => {
                if (this.notificationSettings) {
                    this.notificationSettings[type] = enabled;
                }
                this.alertService.success('artemisApp.userSettings.globalNotificationSettings.updateSuccess');
            },
            error: (error) => onError(this.alertService, error),
        });
    }

    /**
     * Gets the translation key for a notification type label
     * @param type - The notification type
     * @returns The translation key string
     */
    getNotificationTypeLabel(type: GlobalNotificationType): string {
        return `artemisApp.userSettings.globalNotificationSettings.options.${type}`;
    }

    /**
     * Checks if a notification type should be available based on feature flags
     * @param type - The notification type to check
     * @returns true if the notification type should be displayed
     */
    isSettingAvailable(type: GlobalNotificationType): boolean {
        return type !== GLOBAL_NOTIFICATION_TYPES.NEW_PASSKEY_ADDED || this.isPasskeyEnabled;
    }
}
