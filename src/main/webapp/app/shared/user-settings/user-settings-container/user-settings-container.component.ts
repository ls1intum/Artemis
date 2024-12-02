import { Component, OnInit, inject, signal } from '@angular/core';
import { faUser } from '@fortawesome/free-solid-svg-icons';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { PROFILE_LOCALVC } from 'app/app.constants';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { tap } from 'rxjs';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { RouterModule } from '@angular/router';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
/**
 * UserSettingsContainerComponent serves as the common ground for different settings
 */
@Component({
    selector: 'jhi-user-settings',
    templateUrl: 'user-settings-container.component.html',
    standalone: true,
    styleUrls: ['user-settings-container.component.scss'],
    imports: [TranslateDirective, RouterModule, FontAwesomeModule],
})
export class UserSettingsContainerComponent implements OnInit {
    private readonly profileService = inject(ProfileService);
    private readonly accountService = inject(AccountService);

    // Icons
    protected readonly faUser = faUser;

    protected readonly currentUser = signal<User | undefined>(undefined);

    protected readonly localVCEnabled = signal<boolean>(false);
    protected readonly isAtLeastTutor = signal<boolean>(false);

    ngOnInit() {
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            this.localVCEnabled.set(profileInfo.activeProfiles.includes(PROFILE_LOCALVC));
        });
        this.accountService
            .getAuthenticationState()
            .pipe(
                tap((user: User) => {
                    this.currentUser.set(user);
                    this.isAtLeastTutor.set(this.accountService.isAtLeastTutor());
                }),
            )
            .subscribe();
    }
}
