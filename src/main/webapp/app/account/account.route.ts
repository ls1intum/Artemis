import { Routes } from '@angular/router';
import { ActivateComponent } from 'app/account/activate/activate.component';
import { PasswordComponent } from 'app/account/password/password.component';
import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { PasswordResetFinishComponent } from 'app/account/password-reset/finish/password-reset-finish.component';
import { PasswordResetInitComponent } from 'app/account/password-reset/init/password-reset-init.component';
import { RegisterComponent } from 'app/account/register/register.component';
import { SettingsComponent } from 'app/account/settings/settings.component';

export const accountState: Routes = [
    {
        path: '',
        children: [
            {
                path: 'activate',
                component: ActivateComponent,
                data: {
                    authorities: [],
                    pageTitle: 'activate.title',
                },
            },
            {
                path: 'password',
                component: PasswordComponent,
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'global.menu.account.password',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: 'reset/finish',
                component: PasswordResetFinishComponent,
                data: {
                    authorities: [],
                    pageTitle: 'global.menu.account.password',
                },
            },
            {
                path: 'reset/request',
                component: PasswordResetInitComponent,
                data: {
                    authorities: [],
                    pageTitle: 'global.menu.account.password',
                },
            },
            {
                path: 'register',
                component: RegisterComponent,
                data: {
                    authorities: [],
                    pageTitle: 'register.title',
                },
            },
            {
                path: 'settings',
                component: SettingsComponent,
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'global.menu.account.settings',
                },
                canActivate: [UserRouteAccessService],
            },
        ],
    },
];
