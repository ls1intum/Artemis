import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { faWrench } from '@fortawesome/free-solid-svg-icons';
import { User } from 'app/core/user/user.model';

@Component({
    selector: 'jhi-user-management-detail',
    templateUrl: './user-management-detail.component.html',
})
export class UserManagementDetailComponent implements OnInit {
    private route = inject(ActivatedRoute);

    user: User;
    // Icons
    faWrench = faWrench;

    /**
     * Retrieve the user from the user management activated route data subscription
     * and get the user based on the login string
     */
    ngOnInit() {
        this.route.data.subscribe(({ user }) => {
            this.user = user.body ? user.body : user;
        });
    }
}
