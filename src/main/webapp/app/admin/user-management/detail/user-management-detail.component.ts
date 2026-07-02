import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink, RouterOutlet } from '@angular/router';
import { faPencil } from '@fortawesome/free-solid-svg-icons';
import { User } from 'app/account/user/user.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ProfilePictureComponent } from 'app/shared-ui/profile-picture/profile-picture.component';
import { addPublicFilePrefix } from 'app/app.constants';
import { AdminTitleBarTitleDirective } from 'app/admin/shared/admin-title-bar-title.directive';

/**
 * Component for displaying user details in the admin user management.
 * Shows user information like login, name, email, authorities, and groups.
 */
@Component({
    selector: 'jhi-user-management-detail',
    templateUrl: './user-management-detail.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        TranslateDirective,
        RouterLink,
        FaIconComponent,
        RouterOutlet,
        TagModule,
        ButtonModule,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        ProfilePictureComponent,
        AdminTitleBarTitleDirective,
    ],
})
export class UserManagementDetailComponent implements OnInit {
    private readonly route = inject(ActivatedRoute);

    /** The user being displayed */
    readonly user = signal<User | undefined>(undefined);

    /** Icons */
    protected readonly faPencil = faPencil;

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
