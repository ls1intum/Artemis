import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink, RouterOutlet } from '@angular/router';
import { faWrench } from '@fortawesome/free-solid-svg-icons';
import { User } from 'app/core/user/user.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { addPublicFilePrefix } from 'app/app.constants';

/**
 * Component for displaying user details in the admin user management.
 * Shows user information like login, name, email, authorities, and groups.
 */
@Component({
    selector: 'jhi-user-management-detail',
    templateUrl: './user-management-detail.component.html',
    imports: [TranslateDirective, RouterLink, FaIconComponent, RouterOutlet, ArtemisDatePipe, ArtemisTranslatePipe, ProfilePictureComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserManagementDetailComponent implements OnInit {
    private readonly route = inject(ActivatedRoute);

    /** The user being displayed */
    readonly user = signal<User | undefined>(undefined);

    /** Icons */
    protected readonly faWrench = faWrench;

    /** Utility function to add public file prefix to image URLs */
    protected readonly addPublicFilePrefix = addPublicFilePrefix;

    /**
     * Retrieves the user from the route data subscription.
     * Handles both wrapped (HttpResponse) and unwrapped user data.
     */
    ngOnInit(): void {
        this.route.data.subscribe(({ user }) => {
            const resolvedUser = user?.body ?? user;
            this.user.set(resolvedUser);
        });
    }
}
