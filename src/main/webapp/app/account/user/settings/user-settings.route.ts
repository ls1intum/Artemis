import { Routes } from '@angular/router';

import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { IS_AT_LEAST_STUDENT, IS_AT_LEAST_TUTOR } from 'app/foundation/constants/authority.constants';
import { DataGuard } from 'app/account/user/settings/data-guard.service';
import { learnerProfileGuard } from 'app/account/user/settings/learner-profile/learner-profile.guard';
import { scienceSettingsGuard } from 'app/account/user/settings/science-settings/science-settings.guard';

export const routes: Routes = [
    {
        path: 'user-settings',
        loadComponent: () => import('app/account/user/settings/user-settings-container/user-settings-container.component').then((m) => m.UserSettingsContainerComponent),
        canActivate: [UserRouteAccessService],
        data: {
            authorities: IS_AT_LEAST_STUDENT,
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
                loadComponent: () => import('app/account/user/settings/account-information/account-information.component').then((m) => m.AccountInformationComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.accountInformation',
                },
            },
            {
                path: 'llm-usage',
                redirectTo: 'ai-experience',
                pathMatch: 'full',
            },
            {
                path: 'profile',
                loadComponent: () => import('app/account/user/settings/learner-profile/learner-profile.component').then((m) => m.LearnerProfileComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.learnerProfile',
                },
                canActivate: [learnerProfileGuard],
            },
            {
                path: 'science',
                loadComponent: () => import('app/account/user/settings/science-settings/science-settings.component').then((m) => m.ScienceSettingsComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.categories.SCIENCE_SETTINGS',
                },
                canActivate: [scienceSettingsGuard],
            },
            {
                path: 'credentials',
                loadComponent: () =>
                    import('app/account/user/settings/credential-revocation-settings/credential-revocation-settings.component').then(
                        (m) => m.CredentialRevocationSettingsComponent,
                    ),
                data: {
                    pageTitle: 'artemisApp.userSettings.credentialRevocation.title',
                },
            },
            {
                path: 'ssh',
                loadComponent: () => import('app/account/user/settings/ssh-settings/ssh-user-settings.component').then((m) => m.SshUserSettingsComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.categories.SSH_SETTINGS',
                },
            },
            {
                path: 'ssh/add',
                loadComponent: () =>
                    import('app/account/user/settings/ssh-settings/details/ssh-user-settings-key-details.component').then((m) => m.SshUserSettingsKeyDetailsComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.categories.SSH_SETTINGS',
                },
            },
            {
                path: 'ssh/fingerprints',
                loadComponent: () =>
                    import('app/account/user/settings/ssh-settings/fingerprints/ssh-user-settings-fingerprints.component').then((m) => m.SshUserSettingsFingerprintsComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.categories.SSH_SETTINGS',
                },
            },
            {
                path: 'ssh/view/:keyId',
                loadComponent: () =>
                    import('app/account/user/settings/ssh-settings/details/ssh-user-settings-key-details.component').then((m) => m.SshUserSettingsKeyDetailsComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.categories.SSH_SETTINGS',
                },
            },
            {
                path: 'vcs-token',
                loadComponent: () =>
                    import('app/account/user/settings/vcs-access-tokens-settings/vcs-access-tokens-settings.component').then((m) => m.VcsAccessTokensSettingsComponent),
                data: {
                    authorities: IS_AT_LEAST_TUTOR,
                    pageTitle: 'artemisApp.userSettings.categories.VCS_TOKEN_SETTINGS',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: 'access-tokens',
                loadComponent: () =>
                    import('app/account/user/settings/vcs-access-token-overview/vcs-access-token-overview.component').then((m) => m.VcsAccessTokenOverviewComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.vcsAccessTokensOverview.title',
                },
            },
            {
                path: 'passkeys',
                loadComponent: () => import('app/account/user/settings/passkey-settings/passkey-settings.component').then((m) => m.PasskeySettingsComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.categories.PASSKEYS',
                },
            },
            {
                path: 'ide-preferences',
                loadComponent: () => import('app/account/user/settings/ide-preferences/ide-settings.component').then((m) => m.IdeSettingsComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.categories.IDE_PREFERENCES',
                },
            },
            {
                path: 'notifications',
                loadComponent: () =>
                    import('app/account/user/settings/global-notifications-settings/global-notifications-settings.component').then((m) => m.GlobalNotificationsSettingsComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.categories.GLOBAL_NOTIFICATIONS',
                },
            },
            {
                path: 'quiz-training',
                loadComponent: () => import('app/account/user/settings/quiz-training-settings/quiz-training-settings.component').then((m) => m.QuizTrainingSettingsComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.categories.QUIZ_TRAINING_SETTINGS',
                },
            },
            {
                path: 'ai-experience',
                loadComponent: () => import('app/account/user/settings/ai-experience-settings/ai-experience-settings.component').then((m) => m.AiExperienceSettingsComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.aiExperienceSettingsPage.title',
                },
                canActivate: [DataGuard],
            },
        ],
    },
];
