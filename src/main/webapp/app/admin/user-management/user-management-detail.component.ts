import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { User } from 'app/core/user/user.model';

@Component({
    selector: 'jhi-user-management-detail',
    templateUrl: './user-management-detail.component.html',
})
export class UserManagementDetailComponent implements OnInit {
    user: User;

    constructor(private route: ActivatedRoute) {}

    /**
     * Retrieve the user from the user management activated route data {@link UserMgmtResolve} subscription
     * and get the user based on the login string
     */
    ngOnInit() {
        this.route.data.subscribe(({ user }) => {
            this.user = user.body ? user.body : user;
        });
    }
}
