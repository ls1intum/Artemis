import { Routes } from '@angular/router';
import { UserSettingsComponent } from 'app/shared/user-settings/user-settings.component';
import { AccountInformationComponent } from 'app/shared/user-settings/account-information/account-information.component';
import { NotificationSettingsComponent } from 'app/shared/user-settings/notification-settings/notification-settings.component';

export const userSettingsState: Routes = [
    {
        path: 'user-settings',
        component: UserSettingsComponent,
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
            },
            {
                path: 'notifications',
                component: NotificationSettingsComponent,
            },
        ],
    },
];
