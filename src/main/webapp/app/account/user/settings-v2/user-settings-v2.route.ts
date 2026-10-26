import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { IS_AT_LEAST_STUDENT } from 'app/foundation/constants/authority.constants';

export const routes: Routes = [
    {
        path: 'user-settings-v2',
        loadComponent: () => import('./user-settings-v2-container/user-settings-v2-container.component').then((m) => m.UserSettingsV2ContainerComponent),
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
                loadComponent: () => import('./profile-settings-v2/profile-settings-v2.component').then((m) => m.ProfileSettingsV2Component),
                data: {
                    pageTitle: 'artemisApp.userSettings.navigation.profile',
                },
            },
            {
                path: 'credentials',
                loadComponent: () => import('./user-settings-v2-placeholder/user-settings-v2-placeholder.component').then((m) => m.UserSettingsV2PlaceholderComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.navigation.credentials',
                },
            },
            {
                path: 'learning',
                loadComponent: () => import('./user-settings-v2-placeholder/user-settings-v2-placeholder.component').then((m) => m.UserSettingsV2PlaceholderComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.navigation.learning',
                },
            },
            {
                path: 'ai-experience',
                loadComponent: () => import('./user-settings-v2-placeholder/user-settings-v2-placeholder.component').then((m) => m.UserSettingsV2PlaceholderComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.aiExperience',
                },
            },
            {
                path: 'exercises',
                loadComponent: () => import('./user-settings-v2-placeholder/user-settings-v2-placeholder.component').then((m) => m.UserSettingsV2PlaceholderComponent),
                data: {
                    pageTitle: 'artemisApp.userSettings.navigation.exercises',
                },
            },
        ],
    },
];
