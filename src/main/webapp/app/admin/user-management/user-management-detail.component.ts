import { filter } from 'rxjs/operators';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router, NavigationEnd } from '@angular/router';
import { User } from 'app/core/user/user.model';

@Component({
    selector: 'jhi-user-management-detail',
    templateUrl: './user-management-detail.component.html',
})
export class UserManagementDetailComponent implements OnInit {
    user: User;
    isVisible: boolean;

    constructor(private route: ActivatedRoute, private router: Router) {}

    /**
     * Retrieve the user from the user management activated route data {@link UserMgmtResolve} subscription
     * and get the user based on the login string
     */
    ngOnInit() {
        this.route.data.subscribe(({ user }) => {
            this.user = user.body ? user.body : user;
        });
        this.isVisible = this.route.children.length === 0;
        this.router.events.pipe(filter((event) => event instanceof NavigationEnd)).subscribe(() => (this.isVisible = this.route.children.length === 0));
    }
}
