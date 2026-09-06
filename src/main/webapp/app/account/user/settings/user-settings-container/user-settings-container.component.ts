import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
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
import { FeatureToggle, FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';
import { insightsSectionAvailable, isAtlasModuleActive, isIrisModuleActive } from 'app/account/user/settings/learner-profile/learner-profile-availability';
import { TumUiListComponent, TumUiListItemActionDirective, TumUiListItemDirective } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

/**
 * UserSettingsContainerComponent serves as the common ground for different settings
 */
@Component({
    selector: 'jhi-user-settings',
    templateUrl: 'user-settings-container.component.html',
    styleUrls: ['user-settings-container.component.scss'],
    imports: [TranslateDirective, RouterModule, FontAwesomeModule, TumUiListComponent, TumUiListItemDirective, TumUiListItemActionDirective, ArtemisTranslatePipe],
})
export class UserSettingsContainerComponent implements OnInit {
    protected readonly faUser = faUser;

    private readonly profileService = inject(ProfileService);
    private readonly accountService = inject(AccountService);
    private readonly dataGuard = inject(DataGuard);
    private readonly featureToggleService = inject(FeatureToggleService);

    readonly currentUser = signal<User | undefined>(undefined);

    readonly isPasskeyEnabled = signal(false);
    readonly isAtLeastTutor = signal(false);
    readonly isAiEnabled = signal(false);
    // The science settings live in the atlas module (server-side ScienceSettingsResource is @Conditional(AtlasEnabled)).
    // When atlas is disabled the science-settings endpoint does not exist, so the tab must be hidden instead of opening
    // an empty page (issue #13173).
    readonly isAtlasEnabled = signal(false);
    // The learner profile composes sections from the atlas and the iris module, so its tab needs at least one of them.
    // The module state is read into signals in ngOnInit rather than from inside the computed, which may only read
    // signals; insightsSectionAvailable is shared with the page and its route guard.
    private readonly isIrisEnabled = signal(false);
    private readonly isMemirisEnabled = toSignal(this.featureToggleService.getFeatureToggleActive(FeatureToggle.Memiris), { requireSync: true });
    readonly isLearnerProfileEnabled = computed(() => this.isAtlasEnabled() || insightsSectionAvailable(this.isIrisEnabled(), this.isMemirisEnabled()));

    ngOnInit() {
        this.isPasskeyEnabled.set(this.profileService.isModuleFeatureActive(MODULE_FEATURE_PASSKEY));
        this.isAtlasEnabled.set(isAtlasModuleActive(this.profileService));
        this.isIrisEnabled.set(isIrisModuleActive(this.profileService));

        this.isAiEnabled.set(this.dataGuard.isUsingLLM());
        this.accountService
            .getAuthenticationState()
            .pipe(
                tap((user: User | undefined) => {
                    this.currentUser.set(user);
                    this.isAtLeastTutor.set(this.accountService.isAtLeastTutor());
                }),
            )
            .subscribe();
    }

    protected readonly addPublicFilePrefix = addPublicFilePrefix;
}
