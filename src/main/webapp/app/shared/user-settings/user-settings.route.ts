import { Routes } from '@angular/router';
import { UserSettingsContainerComponent } from 'app/shared/user-settings/user-settings-container/user-settings-container.component';
import { AccountInformationComponent } from 'app/shared/user-settings/account-information/account-information.component';
import { NotificationSettingsComponent } from 'app/shared/user-settings/notification-settings/notification-settings.component';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Authority } from 'app/shared/constants/authority.constants';
import { ScienceSettingsComponent } from 'app/shared/user-settings/science-settings/science-settings.component';
import { SshUserSettingsComponent } from 'app/shared/user-settings/ssh-settings/ssh-user-settings.component';
import { VcsAccessTokensSettingsComponent } from 'app/shared/user-settings/vcs-access-tokens-settings/vcs-access-tokens-settings.component';
import { IdeSettingsComponent } from 'app/shared/user-settings/ide-preferences/ide-settings.component';
import { SshUserSettingsKeyDetailsComponent } from 'app/shared/user-settings/ssh-settings/details/ssh-user-settings-key-details.component';

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
                path: 'science',
                component: ScienceSettingsComponent,
                data: {
                    pageTitle: 'artemisApp.userSettings.categories.SCIENCE_SETTINGS',
                },
            },
            {
                path: 'ssh',
                component: SshUserSettingsComponent,
                data: {
                    pageTitle: 'artemisApp.userSettings.categories.SSH_SETTINGS',
                },
            },
            {
                path: 'ssh/add',
                component: SshUserSettingsKeyDetailsComponent,
                data: {
                    pageTitle: 'artemisApp.userSettings.categories.SSH_SETTINGS',
                },
            },
            {
                path: 'ssh/view/:keyId',
                component: SshUserSettingsKeyDetailsComponent,
                data: {
                    pageTitle: 'artemisApp.userSettings.categories.SSH_SETTINGS',
                },
            },
            {
                path: 'vcs-token',
                component: VcsAccessTokensSettingsComponent,
                data: {
                    pageTitle: 'artemisApp.userSettings.categories.VCS_TOKEN_SETTINGS',
                },
            },
            {
                path: 'ide-preferences',
                component: IdeSettingsComponent,
                data: {
                    pageTitle: 'artemisApp.userSettings.categories.IDE_PREFERENCES',
                },
            },
        ],
    },
];
