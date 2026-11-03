import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { IS_AT_LEAST_STUDENT } from 'app/foundation/constants/authority.constants';
import { DataGuard } from 'app/account/user/settings/ai-experience/data-guard.service';
import { learnerProfileGuard } from 'app/account/user/settings/learning/learner-profile.guard';

export const routes: Routes = [
    {
        path: 'user-settings',
        loadComponent: () => import('./navigation/container/user-settings-container.component').then((m) => m.UserSettingsContainerComponent),
        canActivate: [UserRouteAccessService],
        data: {
            authorities: IS_AT_LEAST_STUDENT,
            usesModuleBackground: false,
        },
        children: [
            {
                path: '',
                pathMatch: 'full',
                redirectTo: 'profile',
            },
            {
                path: 'profile',
                loadComponent: () => import('./profile/profile-settings.component').then((m) => m.ProfileSettingsComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.navigation.profile',
                },
            },
            {
                path: 'credentials',
                loadComponent: () => import('./credentials/credentials-settings.component').then((m) => m.CredentialsSettingsComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.navigation.credentials',
                },
            },
            {
                path: 'credentials/ssh/add',
                loadComponent: () =>
                    import('app/account/user/settings/credentials/ssh-settings/details/ssh-user-settings-key-details.component').then((m) => m.SshUserSettingsKeyDetailsComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.categories.SSH_SETTINGS',
                },
            },
            {
                path: 'credentials/ssh/fingerprints',
                loadComponent: () =>
                    import('app/account/user/settings/credentials/ssh-settings/fingerprints/ssh-user-settings-fingerprints.component').then(
                        (m) => m.SshUserSettingsFingerprintsComponent,
                    ),
                data: {
                    pageTitle: 'artemisApp.userSettings.categories.SSH_SETTINGS',
                },
            },
            {
                path: 'credentials/ssh/view/:keyId',
                loadComponent: () =>
                    import('app/account/user/settings/credentials/ssh-settings/details/ssh-user-settings-key-details.component').then((m) => m.SshUserSettingsKeyDetailsComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.categories.SSH_SETTINGS',
                },
            },
            {
                path: 'learning',
                loadComponent: () => import('app/account/user/settings/learning/learner-profile.component').then((m) => m.LearnerProfileComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.navigation.learning',
                },
                canActivate: [learnerProfileGuard],
            },
            {
                path: 'ai-experience',
                loadComponent: () =>
                    import('app/account/user/settings/ai-experience/ai-experience-settings/ai-experience-settings.component').then((m) => m.AiExperienceSettingsComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.aiExperience',
                },
                canActivate: [DataGuard],
            },
            {
                path: 'exercises',
                loadComponent: () => import('./exercises/exercise-settings.component').then((m) => m.ExerciseSettingsComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.navigation.exercises',
                },
            },
            {
                path: 'account',
                pathMatch: 'full',
                redirectTo: 'profile',
            },
            {
                path: 'notifications',
                pathMatch: 'full',
                redirectTo: 'profile',
            },
            {
                path: 'science',
                pathMatch: 'full',
                redirectTo: 'profile',
            },
            {
                path: 'passkeys',
                pathMatch: 'full',
                redirectTo: 'credentials',
            },
            {
                path: 'ssh',
                pathMatch: 'full',
                redirectTo: 'credentials',
            },
            {
                path: 'ssh/add',
                pathMatch: 'full',
                redirectTo: 'credentials/ssh/add',
            },
            {
                path: 'ssh/fingerprints',
                pathMatch: 'full',
                redirectTo: 'credentials/ssh/fingerprints',
            },
            {
                path: 'ssh/view/:keyId',
                pathMatch: 'full',
                redirectTo: 'credentials/ssh/view/:keyId',
            },
            {
                path: 'vcs-token',
                pathMatch: 'full',
                redirectTo: 'credentials',
            },
            {
                path: 'access-tokens',
                pathMatch: 'full',
                redirectTo: 'credentials',
            },
            {
                path: 'agent-access',
                pathMatch: 'full',
                redirectTo: 'credentials',
            },
            {
                path: 'llm-usage',
                pathMatch: 'full',
                redirectTo: 'ai-experience',
            },
            {
                path: 'ide-preferences',
                pathMatch: 'full',
                redirectTo: 'exercises',
            },
            {
                path: 'quiz-training',
                pathMatch: 'full',
                redirectTo: 'exercises',
            },
        ],
    },
];
