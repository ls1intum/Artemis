import { Routes } from '@angular/router';
import { UserSettingsComponent } from 'app/shared/user-settings/user-settings.component';
import { AccountInformationComponent } from 'app/shared/user-settings/account-information/account-information.component';
import { NotificationSettingsComponent } from 'app/shared/user-settings/notification-settings/notification-settings.component';

export const userSettingsState: Routes = [
    /*
    {
        path: 'user-settings',
        component: UserSettingsComponent,
    },
    {
        path: 'account',
        component: AccountInformationComponent,
    },
    {
        path: 'notifications',
        component: NotificationSettingsComponent,
    },
    {//maybe (not) needed? -> goal the accout information should be displayed (first) when going to user-settings
        path: '',
        redirectTo: 'account',
        pathMatch: 'full',
    }
     */
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
