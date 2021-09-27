import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve, Route } from '@angular/router';
import { User } from 'app/core/user/user.model';
import { UserService } from 'app/core/user/user.service';
import { UserManagementDetailComponent } from 'app/admin/user-management/user-management-detail.component';
import { UserManagementComponent } from 'app/admin/user-management/user-management.component';
import { UserManagementUpdateComponent } from 'app/admin/user-management/user-management-update.component';
import { Observable, of } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class UserMgmtResolve implements Resolve<User> {
    constructor(private userService: UserService) {}

    /**
     * Resolve route to find the user before the route is activated
     * @param route  contains the information about a route associated with a component loaded in an outlet at a particular dayjs in time
     */
    resolve(route: ActivatedRouteSnapshot): Observable<User> {
        if (route.params['login']) {
            return this.userService.find(route.params['login']);
        }
        return of(new User());
    }
}

export const userMgmtRoute: Route[] = [
    {
        path: 'user-management',
        component: UserManagementComponent,
        data: {
            pageTitle: 'userManagement.home.title',
            defaultSort: 'id,asc',
        },
    },
    {
        // Create a new path without a component defined to prevent resolver caching and the UserManagementComponent from being always rendered
        path: 'user-management',
        data: {
            pageTitle: 'userManagement.home.title',
        },
        children: [
            {
                path: 'new',
                component: UserManagementUpdateComponent,
                resolve: {
                    user: UserMgmtResolve,
                },
                data: {
                    pageTitle: 'userManagement.home.createLabel',
                },
            },
            {
                path: ':login',
                component: UserManagementDetailComponent,
                resolve: {
                    user: UserMgmtResolve,
                },
                data: {
                    pageTitle: 'userManagement.home.title',
                },
            },
            {
                // Create a new path without a component defined to prevent resolver caching and the UserManagementDetailComponent from being always rendered
                path: ':login',
                resolve: {
                    user: UserMgmtResolve,
                },
                children: [
                    {
                        path: 'edit',
                        component: UserManagementUpdateComponent,
                        data: {
                            pageTitle: 'userManagement.home.createOrEditLabel',
                        },
                    },
                ],
            },
        ],
    },
];
