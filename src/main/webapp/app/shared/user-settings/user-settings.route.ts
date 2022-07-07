import { Routes } from '@angular/router';
import { UserSettingsContainerComponent } from 'app/shared/user-settings/user-settings-container/user-settings-container.component';
import { AccountInformationComponent } from 'app/shared/user-settings/account-information/account-information.component';
import { NotificationSettingsComponent } from 'app/shared/user-settings/notification-settings/notification-settings.component';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Authority } from 'app/shared/constants/authority.constants';

export const userSettingsState: Routes = [
    {
        path: 'user-settings',
        component: UserSettingsContainerComponent,
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
                component: AccountInformationComponent,
                data: {
                    pageTitle: 'artemisApp.userSettings.accountInformation',
                },
            },
            {
                path: 'notifications',
                component: NotificationSettingsComponent,
                data: {
                    pageTitle: 'artemisApp.userSettings.categories.NOTIFICATION_SETTINGS',
                },
            },
            {
                path: 'personal-access-tokens',
                loadChildren: () => import('./personal-access-tokens/personal-access-tokens.module').then((m) => m.PersonalAccessTokensModule),
                data: {
                    pageTitle: 'artemisApp.userSettings.personalAccessTokens.title',
                },
            },
        ],
    },
];
