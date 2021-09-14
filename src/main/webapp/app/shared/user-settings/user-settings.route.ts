import { Routes } from '@angular/router';
import { UserSettingsContainerComponent } from 'app/shared/user-settings/user-settings-container/user-settings-container.component';
import { AccountInformationComponent } from 'app/shared/user-settings/account-information/account-information.component';
import { NotificationSettingsComponent } from 'app/shared/user-settings/notification-settings/notification-settings.component';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

export const userSettingsState: Routes = [
    {
        path: 'user-settings',
        component: UserSettingsContainerComponent,
        canActivate: [UserRouteAccessService],
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
