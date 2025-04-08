import { Routes } from '@angular/router';

import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Authority, IS_AT_LEAST_TUTOR } from 'app/shared/constants/authority.constants';

export const routes: Routes = [
    {
        path: 'user-settings',
        loadComponent: () => import('app/core/user/settings/user-settings-container/user-settings-container.component').then((m) => m.UserSettingsContainerComponent),
        canActivate: [UserRouteAccessService],
        data: {
            authorities: [Authority.USER],
        },
        children: [
            {
                // redirects to account information when user-settings are opened
                path: '',
                pathMatch: 'full',
                redirectTo: 'account',
            },
            {
                path: 'account',
                loadComponent: () => import('app/core/user/settings/account-information/account-information.component').then((m) => m.AccountInformationComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.accountInformation',
                },
            },
            {
                path: 'notifications',
                loadComponent: () => import('app/core/user/settings/notification-settings/notification-settings.component').then((m) => m.NotificationSettingsComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.categories.NOTIFICATION_SETTINGS',
                },
            },
            {
                path: 'science',
                loadComponent: () => import('app/core/user/settings/science-settings/science-settings.component').then((m) => m.ScienceSettingsComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.categories.SCIENCE_SETTINGS',
                },
            },
            {
                path: 'ssh',
                loadComponent: () => import('app/core/user/settings/ssh-settings/ssh-user-settings.component').then((m) => m.SshUserSettingsComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.categories.SSH_SETTINGS',
                },
            },
            {
                path: 'ssh/add',
                loadComponent: () =>
                    import('app/core/user/settings/ssh-settings/details/ssh-user-settings-key-details.component').then((m) => m.SshUserSettingsKeyDetailsComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.categories.SSH_SETTINGS',
                },
            },
            {
                path: 'ssh/fingerprints',
                loadComponent: () =>
                    import('app/core/user/settings/ssh-settings/fingerprints/ssh-user-settings-fingerprints.component').then((m) => m.SshUserSettingsFingerprintsComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.categories.SSH_SETTINGS',
                },
            },
            {
                path: 'ssh/view/:keyId',
                loadComponent: () =>
                    import('app/core/user/settings/ssh-settings/details/ssh-user-settings-key-details.component').then((m) => m.SshUserSettingsKeyDetailsComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.categories.SSH_SETTINGS',
                },
            },
            {
                path: 'vcs-token',
                loadComponent: () =>
                    import('app/core/user/settings/vcs-access-tokens-settings/vcs-access-tokens-settings.component').then((m) => m.VcsAccessTokensSettingsComponent),
                data: {
                    authorities: IS_AT_LEAST_TUTOR,
                    pageTitle: 'artemisApp.userSettings.categories.VCS_TOKEN_SETTINGS',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: 'passkeys',
                loadComponent: () => import('app/shared/user-settings/passkey-settings/passkey-settings.component').then((m) => m.PasskeySettingsComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.categories.PASSKEYS',
                },
            },
            {
                path: 'ide-preferences',
                loadComponent: () => import('app/core/user/settings/ide-preferences/ide-settings.component').then((m) => m.IdeSettingsComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.categories.IDE_PREFERENCES',
                },
            },
        ],
    },
];
