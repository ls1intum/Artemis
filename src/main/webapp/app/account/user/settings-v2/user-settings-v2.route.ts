import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { IS_AT_LEAST_STUDENT } from 'app/foundation/constants/authority.constants';
import { DataGuard } from 'app/account/user/settings/data-guard.service';
import { learnerProfileGuard } from 'app/account/user/settings/learner-profile/learner-profile.guard';

export const routes: Routes = [
    {
        path: 'user-settings-v2',
        loadComponent: () => import('./navigation/container/user-settings-v2-container.component').then((m) => m.UserSettingsV2ContainerComponent),
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
                loadComponent: () => import('./profile/profile-settings-v2.component').then((m) => m.ProfileSettingsV2Component),
                data: {
                    pageTitle: 'artemisApp.userSettings.navigation.profile',
                },
            },
            {
                path: 'credentials',
                loadComponent: () => import('./credentials/credentials-settings-v2.component').then((m) => m.CredentialsSettingsV2Component),
                data: {
                    pageTitle: 'artemisApp.userSettings.navigation.credentials',
                },
            },
            {
                path: 'credentials/ssh/add',
                loadComponent: () =>
                    import('app/account/user/settings/ssh-settings/details/ssh-user-settings-key-details.component').then((m) => m.SshUserSettingsKeyDetailsComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.categories.SSH_SETTINGS',
                },
            },
            {
                path: 'credentials/ssh/fingerprints',
                loadComponent: () =>
                    import('app/account/user/settings/ssh-settings/fingerprints/ssh-user-settings-fingerprints.component').then((m) => m.SshUserSettingsFingerprintsComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.categories.SSH_SETTINGS',
                },
            },
            {
                path: 'credentials/ssh/view/:keyId',
                loadComponent: () =>
                    import('app/account/user/settings/ssh-settings/details/ssh-user-settings-key-details.component').then((m) => m.SshUserSettingsKeyDetailsComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.categories.SSH_SETTINGS',
                },
            },
            {
                path: 'learning',
                loadComponent: () => import('app/account/user/settings/learner-profile/learner-profile.component').then((m) => m.LearnerProfileComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.navigation.learning',
                },
                canActivate: [learnerProfileGuard],
            },
            {
                path: 'ai-experience',
                loadComponent: () => import('app/account/user/settings/ai-experience-settings/ai-experience-settings.component').then((m) => m.AiExperienceSettingsComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.aiExperience',
                },
                canActivate: [DataGuard],
            },
            {
                path: 'exercises',
                loadComponent: () => import('./exercises/exercise-settings-v2.component').then((m) => m.ExerciseSettingsV2Component),
                data: {
                    pageTitle: 'artemisApp.userSettings.navigation.exercises',
                },
            },
        ],
    },
];
