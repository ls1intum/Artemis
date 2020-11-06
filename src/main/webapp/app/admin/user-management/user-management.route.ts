import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve, Route } from '@angular/router';
import { JhiResolvePagingParams } from 'ng-jhipster';
import { User } from 'app/core/user/user.model';
import { UserService } from 'app/core/user/user.service';
import { UserManagementDetailComponent } from 'app/admin/user-management/user-management-detail.component';
import { UserManagementComponent } from 'app/admin/user-management/user-management.component';
import { UserManagementUpdateComponent } from 'app/admin/user-management/user-management-update.component';

@Injectable({ providedIn: 'root' })
export class UserMgmtResolve implements Resolve<any> {
    constructor(private userService: UserService) {}

    /**
     * Resolve route to find the user before the route is activated
     * @param route  contains the information about a route associated with a component loaded in an outlet at a particular moment in time
     */
    resolve(route: ActivatedRouteSnapshot) {
        if (route.params['login']) {
            return this.userService.find(route.params['login']);
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
    path: 'user-management/new',
    component: UserManagementUpdateComponent,
    resolve: {
        user: UserMgmtResolve,
    },
    data: {
        pageTitle: 'userManagement.home.createLabel',
    },
};
export const userMgmtRoute3: Route = {
    path: 'user-management/:login',
    component: UserManagementDetailComponent,
    resolve: {
        user: UserMgmtResolve,
    },
    data: {
        pageTitle: 'userManagement.home.title',
    },
};

export const userMgmtRoute4: Route = {
    path: 'user-management/:login/edit',
    component: UserManagementUpdateComponent,
    resolve: {
        user: UserMgmtResolve,
    },
    data: {
        pageTitle: 'userManagement.home.createOrEditLabel',
    },
};
