import { Component, OnInit, inject, signal } from '@angular/core';
import { faUser } from '@fortawesome/free-solid-svg-icons';
import { MODULE_FEATURE_PASSKEY, addPublicFilePrefix } from 'app/app.constants';
import { User } from 'app/account/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { RouterModule } from '@angular/router';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { tap } from 'rxjs';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { DataGuard } from 'app/account/user/settings/data-guard.service';

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
    private readonly dataGuard = inject(DataGuard);

    readonly currentUser = signal<User | undefined>(undefined);

    readonly isPasskeyEnabled = signal(false);
    readonly isAtLeastTutor = signal(false);
    readonly isAiEnabled = signal(false);

    ngOnInit() {
        this.isPasskeyEnabled.set(this.profileService.isModuleFeatureActive(MODULE_FEATURE_PASSKEY));

        this.isAiEnabled.set(this.dataGuard.isUsingLLM());
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

    protected readonly addPublicFilePrefix = addPublicFilePrefix;
}
