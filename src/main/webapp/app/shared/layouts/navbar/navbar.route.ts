import { Route } from '@angular/router';

import { NavbarComponent } from './navbar.component';
import { LogoutComponent } from 'app/shared/layouts/navbar/logout/logout.component';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

export const navbarRoute: Route[] = [
    {
        path: '',
        component: NavbarComponent,
        outlet: 'navbar',
    },
    {
        path: 'logout',
        component: LogoutComponent,
        canActivate: [UserRouteAccessService],
    },
];
