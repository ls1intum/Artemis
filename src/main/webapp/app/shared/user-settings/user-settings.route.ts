import { Routes } from '@angular/router';

import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Authority } from 'app/shared/constants/authority.constants';

export const userSettingsState: Routes = [
    {
        path: 'user-settings',
        loadComponent: () => import('app/shared/user-settings/user-settings-container/user-settings-container.component').then((m) => m.UserSettingsContainerComponent),
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
                loadComponent: () => import('app/shared/user-settings/account-information/account-information.component').then((m) => m.AccountInformationComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.accountInformation',
                },
            },
            {
                path: 'notifications',
                loadComponent: () => import('app/shared/user-settings/notification-settings/notification-settings.component').then((m) => m.NotificationSettingsComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.categories.NOTIFICATION_SETTINGS',
                },
            },
            {
                path: 'science',
                loadComponent: () => import('app/shared/user-settings/science-settings/science-settings.component').then((m) => m.ScienceSettingsComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.categories.SCIENCE_SETTINGS',
                },
            },
            {
                path: 'ssh',
                loadComponent: () => import('app/shared/user-settings/ssh-settings/ssh-user-settings.component').then((m) => m.SshUserSettingsComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.categories.SSH_SETTINGS',
                },
            },
            {
                path: 'ssh/add',
                loadComponent: () =>
                    import('app/shared/user-settings/ssh-settings/details/ssh-user-settings-key-details.component').then((m) => m.SshUserSettingsKeyDetailsComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.categories.SSH_SETTINGS',
                },
            },
            {
                path: 'ssh/fingerprints',
                loadComponent: () =>
                    import('app/shared/user-settings/ssh-settings/fingerprints/ssh-user-settings-fingerprints.component').then((m) => m.SshUserSettingsFingerprintsComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.categories.SSH_SETTINGS',
                },
            },
            {
                path: 'ssh/view/:keyId',
                loadComponent: () =>
                    import('app/shared/user-settings/ssh-settings/details/ssh-user-settings-key-details.component').then((m) => m.SshUserSettingsKeyDetailsComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.categories.SSH_SETTINGS',
                },
            },
            {
                path: 'vcs-token',
                loadComponent: () =>
                    import('app/shared/user-settings/vcs-access-tokens-settings/vcs-access-tokens-settings.component').then((m) => m.VcsAccessTokensSettingsComponent),
                data: {
                    authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
                    pageTitle: 'artemisApp.userSettings.categories.VCS_TOKEN_SETTINGS',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: 'ide-preferences',
                loadComponent: () => import('app/shared/user-settings/ide-preferences/ide-settings.component').then((m) => m.IdeSettingsComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.categories.IDE_PREFERENCES',
                },
            },
        ],
    },
];
