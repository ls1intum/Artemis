import { Route } from '@angular/router';

import { NavbarComponent } from './navbar.component';
import { LogoutComponent } from 'app/shared/layouts/navbar/logout/logout.component';

export const navbarRoute: Route[] = [
    {
        path: '',
        component: NavbarComponent,
        outlet: 'navbar',
    },
    {
        path: 'logout',
        component: LogoutComponent,
    },
];
