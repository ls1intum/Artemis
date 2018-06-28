import { Routes } from '@angular/router';

import { activateRoute, registerRoute, settingsRoute } from './';

const ACCOUNT_ROUTES = [
    activateRoute,
    registerRoute,
    settingsRoute
];

export const accountState: Routes = [{
    path: '',
    children: ACCOUNT_ROUTES
}];
