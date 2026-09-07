import { Component, OnInit, Signal, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { faUser } from '@fortawesome/free-solid-svg-icons';
import { MODULE_FEATURE_PASSKEY, addPublicFilePrefix } from 'app/app.constants';
import { User } from 'app/account/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { RouterModule } from '@angular/router';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
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

    // Read straight from the account service's signal instead of taking a snapshot from the
    // authentication state observable: that is a BehaviorSubject driven by the log-in / log-out effect, so
    // it does not emit when the identity changes while the user stays logged in. Uploading or deleting a
    // profile picture goes through AccountService.setImageUrl, which replaces userIdentity, so a snapshot
    // taken here went stale and the sidebar kept the previous picture until the next full page load.
    readonly currentUser: Signal<User | undefined> = this.accountService.userIdentity;

    readonly isPasskeyEnabled = signal(false);
    // Derived for the same reason: isAtLeastTutor reads userIdentity, so the computed re-evaluates
    // whenever the identity changes.
    readonly isAtLeastTutor = computed(() => this.accountService.isAtLeastTutor());
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
    }

    protected readonly addPublicFilePrefix = addPublicFilePrefix;
}
