import { Route, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { IS_AT_LEAST_STUDENT } from 'app/foundation/constants/authority.constants';
import { DataGuard } from 'app/account/user/settings/ai-experience/data-guard.service';
import { learnerProfileGuard } from 'app/account/user/settings/learning/learner-profile.guard';

type SettingsPage = 'profile' | 'credentials' | 'credentials/ssh/add' | 'credentials/ssh/fingerprints' | 'credentials/ssh/view/:keyId' | 'ai-experience' | 'exercises';

const legacySettingsAliases: readonly (readonly [alias: string, target: SettingsPage])[] = [
    ['account', 'profile'],
    ['notifications', 'profile'],
    ['science', 'profile'],
    ['passkeys', 'credentials'],
    ['ssh', 'credentials'],
    ['ssh/add', 'credentials/ssh/add'],
    ['ssh/fingerprints', 'credentials/ssh/fingerprints'],
    ['ssh/view/:keyId', 'credentials/ssh/view/:keyId'],
    ['vcs-token', 'credentials'],
    ['access-tokens', 'credentials'],
    ['agent-access', 'credentials'],
    ['llm-usage', 'ai-experience'],
    ['ide-preferences', 'exercises'],
    ['quiz-training', 'exercises'],
];

function settingsRedirect([path, redirectTo]: readonly [string, SettingsPage]): Route {
    return { path, pathMatch: 'full', redirectTo };
}

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
                    pageTitle: 'artemisApp.userSettings.navigation.aiExperience',
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
            ...legacySettingsAliases.map(settingsRedirect),
        ],
    },
];
