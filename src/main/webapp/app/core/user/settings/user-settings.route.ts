import { Routes } from '@angular/router';

import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { IS_AT_LEAST_TUTOR, IS_AT_LEAST_USER } from 'app/shared/constants/authority.constants';
import { ExternalDataGuard } from 'app/core/user/settings/external-data.guard';

export const routes: Routes = [
    {
        path: 'user-settings',
        loadComponent: () => import('app/core/user/settings/user-settings-container/user-settings-container.component').then((m) => m.UserSettingsContainerComponent),
        canActivate: [UserRouteAccessService],
        data: {
            authorities: IS_AT_LEAST_USER,
            usesModuleBackground: true,
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
                path: 'external-data',
                loadComponent: () =>
                    import('app/core/user/settings/external-llm-usage-settings/external-llm-usage-settings.component').then((m) => m.ExternalLlmUsageSettingsComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.categories.externalLLMUsage',
                },
                canActivate: [ExternalDataGuard],
            },
            {
                path: 'profile',
                loadComponent: () => import('app/core/user/settings/learner-profile/learner-profile.component').then((m) => m.LearnerProfileComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.learnerProfile',
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
                loadComponent: () => import('app/core/user/settings/passkey-settings/passkey-settings.component').then((m) => m.PasskeySettingsComponent),
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
            {
                path: 'notifications',
                loadComponent: () =>
                    import('app/core/user/settings/global-notifications-settings/global-notifications-settings.component').then((m) => m.GlobalNotificationsSettingsComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.categories.GLOBAL_NOTIFICATIONS',
                },
            },
            {
                path: 'quiz-training',
                loadComponent: () => import('app/core/user/settings/quiz-training-settings/quiz-training-settings.component').then((m) => m.QuizTrainingSettingsComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.categories.QUIZ_TRAINING_SETTINGS',
                },
            },
        ],
    },
];
