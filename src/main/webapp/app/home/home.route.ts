import { Routes } from '@angular/router';

import { HomeComponent } from './';
import { JhiLoginComponent } from 'app/shared';

export const HOME_ROUTES: Routes = [
    {
        path: '',
        component: HomeComponent,
        data: {
            authorities: [],
            pageTitle: 'home.title'
        }
    }, {
        path: 'login',
        component: JhiLoginComponent,
        data: {
            authorities: [],
            pageTitle: 'home.login'
        }
    }
];
