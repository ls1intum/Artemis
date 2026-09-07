import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FeatureToggle, FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { onError } from 'app/foundation/util/global.utils';
import { faExternalLinkAlt } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TumUiButtonComponent, TumUiMessageComponent, TumUiTagComponent, TumUiToggleSwitchComponent, TumUiTooltipDirective } from '@tumaet/ui-angular';
import { AdminTitleBarTitleDirective } from 'app/admin/shared/admin-title-bar-title.directive';
import { AdminTitleBarActionsDirective } from 'app/admin/shared/admin-title-bar-actions.directive';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import {
    MODULE_FEATURE_APOLLON,
    MODULE_FEATURE_ATHENA,
    MODULE_FEATURE_ATLAS,
    MODULE_FEATURE_DEIMOS,
    MODULE_FEATURE_EXAM,
    MODULE_FEATURE_FILEUPLOAD,
    MODULE_FEATURE_HYPERION,
    MODULE_FEATURE_IRIS,
    MODULE_FEATURE_LDAP,
    MODULE_FEATURE_LECTURE,
    MODULE_FEATURE_LTI,
    MODULE_FEATURE_MODELING,
    MODULE_FEATURE_PASSKEY,
    MODULE_FEATURE_PASSKEY_REQUIRE_ADMIN,
    MODULE_FEATURE_PLAGIARISM,
    MODULE_FEATURE_SAML2,
    MODULE_FEATURE_SHARING,
    MODULE_FEATURE_TEXT,
    MODULE_FEATURE_THEIA,
    MODULE_FEATURE_TUTORIALGROUP,
    ModuleFeature,
    PROFILE_BUILDAGENT,
    PROFILE_JENKINS,
    PROFILE_LOCALCI,
    ProfileFeature,
} from 'app/app.constants';
import { cloneWith } from 'app/foundation/util/deep-clone.util';

type FeatureToggleInfo = {
    feature: FeatureToggle;
    isActive: boolean;
    documentationLink?: string;
};

type ProfileFeatureInfo = {
    profile: ProfileFeature;
    isActive: boolean;
    documentationLink?: string;
};

type ModuleFeatureInfo = {
    feature: ModuleFeature;
    isActive: boolean;
    documentationLink?: string;
};

/**
 * Admin component for managing feature toggles.
 * Allows administrators to enable or disable features at runtime.
 */
@Component({
    selector: 'jhi-feature-toggles',
    templateUrl: './admin-feature-toggle.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        FaIconComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        TumUiToggleSwitchComponent,
        TumUiMessageComponent,
        FormsModule,
        AdminTitleBarTitleDirective,
        AdminTitleBarActionsDirective,
        TumUiButtonComponent,
        TumUiTagComponent,
        TumUiTooltipDirective,
    ],
})
export class AdminFeatureToggleComponent implements OnInit {
    private readonly featureToggleService = inject(FeatureToggleService);
    private readonly profileService = inject(ProfileService);
    private readonly destroyRef = inject(DestroyRef);
    private readonly alertService = inject(AlertService);

    /** Available feature toggles with their current state */
    readonly featureToggles = signal<FeatureToggleInfo[]>([]);

    /** Profile-based features with their current state */
    readonly profileFeatures = signal<ProfileFeatureInfo[]>([]);

    /** Module features with their current state */
    readonly moduleFeatures = signal<ModuleFeatureInfo[]>([]);

    /** Icons */
    protected readonly faExternalLinkAlt = faExternalLinkAlt;

    /**
     * Tint and border for a feature card: light green when the feature is active, muted grey when it is not.
     *
     * The contrast comes from the active state being tinted at all — the shades this replaces (`bg-surface-50` vs
     * `bg-surface-100`) were one step apart and indistinguishable at a glance. Red is reserved for states that need
     * attention, so a switched-off feature stays neutral rather than reading as a failure.
     *
     * The success token carries its own light/dark values; the surface shades need explicit `dark:` variants.
     */
    protected featureCardClasses(isActive: boolean): string {
        return isActive ? 'bg-state-success/10 border-state-success/40' : 'bg-surface-100 border-surface-300 dark:bg-surface-800 dark:border-surface-600';
    }

    /** Profiles to display (excluding internal profiles like dev, prod, test) */
    private readonly displayedProfiles: ProfileFeature[] = [PROFILE_LOCALCI, PROFILE_BUILDAGENT, PROFILE_JENKINS];

    /** Module features to display */
    private readonly displayedModuleFeatures: ModuleFeature[] = [
        MODULE_FEATURE_IRIS,
        MODULE_FEATURE_ATLAS,
        MODULE_FEATURE_HYPERION,
        MODULE_FEATURE_DEIMOS,
        MODULE_FEATURE_EXAM,
        MODULE_FEATURE_PLAGIARISM,
        MODULE_FEATURE_TEXT,
        MODULE_FEATURE_MODELING,
        MODULE_FEATURE_FILEUPLOAD,
        MODULE_FEATURE_LECTURE,
        MODULE_FEATURE_TUTORIALGROUP,
        MODULE_FEATURE_SHARING,
        MODULE_FEATURE_LTI,
        MODULE_FEATURE_ATHENA,
        MODULE_FEATURE_APOLLON,
        MODULE_FEATURE_LDAP,
        MODULE_FEATURE_SAML2,
        MODULE_FEATURE_PASSKEY,
        MODULE_FEATURE_PASSKEY_REQUIRE_ADMIN,
        MODULE_FEATURE_THEIA,
    ];

    /** Documentation links for runtime feature toggles */
    private readonly documentationLinks: Partial<Record<FeatureToggle, string>> = {
        [FeatureToggle.ProgrammingExercises]: 'https://docs.artemis.tum.de/instructor/exercises/programming-exercise',
        [FeatureToggle.PlagiarismChecks]: 'https://docs.artemis.tum.de/instructor/plagiarism-check',
        [FeatureToggle.Exports]: 'https://docs.artemis.tum.de/instructor/exports',
        [FeatureToggle.LearningPaths]: 'https://docs.artemis.tum.de/instructor/adaptive-learning',
        [FeatureToggle.StandardizedCompetencies]: 'https://docs.artemis.tum.de/admin/adaptive-learning',
        [FeatureToggle.TutorSuggestions]: 'https://docs.artemis.tum.de/instructor/communication#tutor-suggestions',
        [FeatureToggle.AtlasML]: 'https://docs.artemis.tum.de/admin/artemis-intelligence',
        [FeatureToggle.AtlasAgent]: 'https://docs.artemis.tum.de/admin/artemis-intelligence',
        [FeatureToggle.Memiris]: 'https://docs.artemis.tum.de/admin/extensions-setup#iris--pyris-setup-guide',
        [FeatureToggle.RateLimit]: 'https://docs.artemis.tum.de/admin/production-setup/security/#rate-limiting',
        [FeatureToggle.Deimos]: 'https://docs.artemis.tum.de/admin/artemis-intelligence',
    };

    /** Documentation links for profile-based features */
    private readonly profileDocumentationLinks: Partial<Record<ProfileFeature, string>> = {
        [PROFILE_LOCALCI]: 'https://docs.artemis.tum.de/developer/setup#integrated-code-lifecycle-setup',
        [PROFILE_BUILDAGENT]: 'https://docs.artemis.tum.de/developer/setup#integrated-code-lifecycle-setup',
        [PROFILE_JENKINS]: 'https://docs.artemis.tum.de/developer/jenkins-localvc',
    };

    /** Documentation links for module features */
    private readonly moduleDocumentationLinks: Partial<Record<ModuleFeature, string>> = {
        [MODULE_FEATURE_IRIS]: 'https://docs.artemis.tum.de/admin/extensions-setup#iris--pyris-setup-guide',
        [MODULE_FEATURE_ATLAS]: 'https://docs.artemis.tum.de/instructor/adaptive-learning',
        [MODULE_FEATURE_HYPERION]: 'https://docs.artemis.tum.de/admin/hyperion',
        [MODULE_FEATURE_DEIMOS]: 'https://docs.artemis.tum.de/admin/artemis-intelligence',
        [MODULE_FEATURE_EXAM]: 'https://docs.artemis.tum.de/instructor/exams/intro',
        [MODULE_FEATURE_PLAGIARISM]: 'https://docs.artemis.tum.de/instructor/plagiarism-check',
        [MODULE_FEATURE_TEXT]: 'https://docs.artemis.tum.de/instructor/exercises/text-exercise',
        [MODULE_FEATURE_MODELING]: 'https://docs.artemis.tum.de/instructor/exercises/modeling-exercise',
        [MODULE_FEATURE_FILEUPLOAD]: 'https://docs.artemis.tum.de/instructor/exercises/file-upload-exercise',
        [MODULE_FEATURE_LECTURE]: 'https://docs.artemis.tum.de/instructor/lectures',
        [MODULE_FEATURE_TUTORIALGROUP]: 'https://docs.artemis.tum.de/instructor/tutorial-groups',
        [MODULE_FEATURE_SHARING]: 'https://docs.artemis.tum.de/admin/extensions-setup#setup-guide-for-exchange-with-the-sharing-platform',
        [MODULE_FEATURE_LTI]: 'https://docs.artemis.tum.de/instructor/lti-configuration',
        [MODULE_FEATURE_ATHENA]: 'https://docs.artemis.tum.de/admin/extensions-setup#athena-service',
        [MODULE_FEATURE_APOLLON]: 'https://docs.artemis.tum.de/instructor/exercises/modeling-exercise',
        [MODULE_FEATURE_LDAP]: 'https://docs.artemis.tum.de/admin/production-setup/security#ldap-authentication',
        [MODULE_FEATURE_SAML2]: 'https://docs.artemis.tum.de/admin/saml2-login-registration',
        [MODULE_FEATURE_PASSKEY]: 'https://docs.artemis.tum.de/admin/production-setup/security#passkey-authentication',
        [MODULE_FEATURE_PASSKEY_REQUIRE_ADMIN]: 'https://docs.artemis.tum.de/admin/production-setup/security#passkey-authentication',
        [MODULE_FEATURE_THEIA]: 'https://docs.artemis.tum.de',
    };

    ngOnInit(): void {
        // Load runtime feature toggles
        this.featureToggleService
            .getFeatureToggles()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (activeToggles) => {
                    this.featureToggles.set(
                        Object.values(FeatureToggle).map((feature) => ({
                            feature,
                            isActive: activeToggles.includes(feature),
                            documentationLink: this.documentationLinks[feature],
                        })),
                    );
                },
                error: (error: HttpErrorResponse) => onError(this.alertService, error),
            });

        // Load profile-based features
        this.profileFeatures.set(
            this.displayedProfiles.map((profile) => ({
                profile,
                isActive: this.profileService.isProfileActive(profile),
                documentationLink: this.profileDocumentationLinks[profile],
            })),
        );

        // Load module features
        this.moduleFeatures.set(
            this.displayedModuleFeatures.map((feature) => ({
                feature,
                isActive: this.profileService.isModuleFeatureActive(feature),
                documentationLink: this.moduleDocumentationLinks[feature],
            })),
        );
    }

    /** Features with an in-flight update; their switch is disabled so updates are serialized per feature. */
    readonly pendingFeatures = signal<ReadonlySet<FeatureToggle>>(new Set());

    onFeatureToggle(featureInfo: FeatureToggleInfo): void {
        const feature = featureInfo.feature;
        // Serialize updates per feature: while a request is in flight the switch is disabled, and any stray change
        // is ignored here. Sending only one request at a time keeps the server writes in click order (last click
        // wins) — otherwise two successful writes could reach the server out of order and leave it on the older
        // value, and a late failure could race the optimistic UI.
        if (this.pendingFeatures().has(feature)) {
            return;
        }
        const newState = !featureInfo.isActive;
        // Optimistically reflect the new state so the signal, the [ngModel]-bound switch, and the server request agree.
        this.setToggleState(feature, newState);
        this.setPending(feature, true);
        this.featureToggleService
            .setFeatureToggleState(feature, newState)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: () => this.setPending(feature, false),
                error: (error: HttpErrorResponse) => {
                    // No newer request for this feature can exist (it was disabled while pending), so reverting the
                    // optimistic change safely restores the true server state and flips the switch back; surface the
                    // error instead of leaving the switch silently flipped.
                    this.setToggleState(feature, !newState);
                    this.setPending(feature, false);
                    onError(this.alertService, error);
                },
            });
    }

    private setToggleState(feature: FeatureToggle, isActive: boolean): void {
        this.featureToggles.update((toggles) => toggles.map((toggle) => (toggle.feature === feature ? cloneWith(toggle, { isActive }) : toggle)));
    }

    private setPending(feature: FeatureToggle, pending: boolean): void {
        this.pendingFeatures.update((features) => {
            const next = new Set(features);
            if (pending) {
                next.add(feature);
            } else {
                next.delete(feature);
            }
            return next;
        });
    }

    /**
     * Get the translation key for a profile feature's name
     */
    getProfileNameKey(profile: ProfileFeature): string {
        return `artemisApp.features.profiles.${profile}.name`;
    }

    /**
     * Get the translation key for a profile feature's description
     */
    getProfileDescriptionKey(profile: ProfileFeature): string {
        return `artemisApp.features.profiles.${profile}.description`;
    }

    /**
     * Get the translation key for a module feature's name
     */
    getModuleFeatureNameKey(feature: ModuleFeature): string {
        return `artemisApp.features.modules.${feature}.name`;
    }

    /**
     * Get the translation key for a module feature's description
     */
    getModuleFeatureDescriptionKey(feature: ModuleFeature): string {
        return `artemisApp.features.modules.${feature}.description`;
    }

    /**
     * Get the translation key for a runtime feature toggle's name
     */
    getFeatureNameKey(feature: FeatureToggle): string {
        return `artemisApp.features.toggles.${feature}.name`;
    }

    /**
     * Get the translation key for a runtime feature toggle's description
     */
    getFeatureDescriptionKey(feature: FeatureToggle): string {
        return `artemisApp.features.toggles.${feature}.description`;
    }

    /**
     * Get the translation key for a runtime feature toggle's disable warning
     */
    getFeatureWarningKey(feature: FeatureToggle): string {
        return `artemisApp.features.toggles.${feature}.disableWarning`;
    }

    /**
     * Scroll to a section by its ID
     */
    scrollToSection(sectionId: string): void {
        const element = document.getElementById(sectionId);
        if (element) {
            element.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
    }
}
