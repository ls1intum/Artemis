import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve, Route, RouterStateSnapshot } from '@angular/router';
import { JhiResolvePagingParams } from 'ng-jhipster';

import { UserManagementComponent, UserManagementDetailComponent, UserManagementUpdateComponent } from 'app/admin';
import { User } from 'app/core/user/user.model';
import { UserService } from 'app/core/user/user.service';

@Injectable({ providedIn: 'root' })
export class UserMgmtResolve implements Resolve<any> {
    constructor(private userService: UserService) {}

    /**
     * Resolve route to find the user before the route is activated
     * @param route  contains the information about a route associated with a component loaded in an outlet at a particular moment in time
     * @param state  represents the state of the router at a moment in time
     */
    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const login = route.params['login'] ? route.params['login'] : null;
        if (login) {
            return this.userService.find(login);
        }
        return new User();
    }
}

export const userMgmtRoute1: Route = {
    path: 'user-management',
    component: UserManagementComponent,
    resolve: {
        pagingParams: JhiResolvePagingParams,
    },
    data: {
        pageTitle: 'userManagement.home.title',
        defaultSort: 'id,asc',
    },
};

export const userMgmtRoute2: Route = {
    path: 'user-management/:login/view',
    component: UserManagementDetailComponent,
    resolve: {
        user: UserMgmtResolve,
    },
    data: {
        pageTitle: 'userManagement.home.title',
    },
};
export const userMgmtRoute3: Route = {
    path: 'user-management/new',
    component: UserManagementUpdateComponent,
    resolve: {
        user: UserMgmtResolve,
    },
};

export const userMgmtRoute4: Route = {
    path: 'user-management/:login/edit',
    component: UserManagementUpdateComponent,
    resolve: {
        user: UserMgmtResolve,
    },
};
