import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve } from '@angular/router';
import { User } from 'app/core/user/user.model';
import { UserService } from 'app/core/user/user.service';
import { Observable, of } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class UserManagementResolve implements Resolve<User> {
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
