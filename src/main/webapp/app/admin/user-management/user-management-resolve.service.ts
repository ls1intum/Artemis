import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve } from '@angular/router';
import { User } from 'app/core/user/user.model';
import { Observable, of } from 'rxjs';
import { AdminUserService } from 'app/core/user/admin-user.service';

@Injectable({ providedIn: 'root' })
export class UserManagementResolve implements Resolve<User> {
    private adminUserService = inject(AdminUserService);

    /**
     * Resolve route to find the user before the route is activated
     * @param route  contains the information about a route associated with a component loaded in an outlet at a particular dayjs in time
     */
    resolve(route: ActivatedRouteSnapshot): Observable<User> {
        if (route.params['login']) {
            return this.adminUserService.findUser(route.params['login']);
        }
        return of(new User());
    }
}
