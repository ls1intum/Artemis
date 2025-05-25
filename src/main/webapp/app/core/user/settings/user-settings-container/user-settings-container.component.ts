import { Component, OnInit, inject } from '@angular/core';
import { faUser } from '@fortawesome/free-solid-svg-icons';
import { FEATURE_PASSKEY, addPublicFilePrefix } from 'app/app.constants';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { RouterModule } from '@angular/router';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { tap } from 'rxjs';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';

/**
 * UserSettingsContainerComponent serves as the common ground for different settings
 */
@Component({
    selector: 'jhi-user-settings',
    templateUrl: 'user-settings-container.component.html',
    styleUrls: ['user-settings-container.component.scss'],
    imports: [TranslateDirective, RouterModule, FontAwesomeModule],
})
export class UserSettingsContainerComponent implements OnInit {
    protected readonly faUser = faUser;

    private readonly profileService = inject(ProfileService);
    private readonly accountService = inject(AccountService);

    currentUser?: User;
    isPasskeyEnabled = false;
    isAtLeastTutor = false;

    ngOnInit() {
        this.isPasskeyEnabled = this.profileService.isModuleFeatureActive(FEATURE_PASSKEY);

        this.accountService
            .getAuthenticationState()
            .pipe(
                tap((user: User) => {
                    this.currentUser = user;
                    this.isAtLeastTutor = this.accountService.isAtLeastTutor();
                }),
            )
            .subscribe();
    }

    protected readonly addPublicFilePrefix = addPublicFilePrefix;
}
