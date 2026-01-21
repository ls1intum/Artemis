import { ChangeDetectorRef, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateService } from '@ngx-translate/core';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { faExternalLinkAlt, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { AdminTitleBarTitleDirective } from 'app/core/admin/shared/admin-title-bar-title.directive';
import { AdminTitleBarActionsDirective } from 'app/core/admin/shared/admin-title-bar-actions.directive';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import {
    MODULE_FEATURE_ATLAS,
    MODULE_FEATURE_EXAM,
    MODULE_FEATURE_FILEUPLOAD,
    MODULE_FEATURE_HYPERION,
    MODULE_FEATURE_LECTURE,
    MODULE_FEATURE_LTI,
    MODULE_FEATURE_MODELING,
    MODULE_FEATURE_NEBULA,
    MODULE_FEATURE_PASSKEY,
    MODULE_FEATURE_PLAGIARISM,
    MODULE_FEATURE_SHARING,
    MODULE_FEATURE_TEXT,
    MODULE_FEATURE_TUTORIALGROUP,
    ModuleFeature,
    PROFILE_AEOLUS,
    PROFILE_APOLLON,
    PROFILE_ATHENA,
    PROFILE_BUILDAGENT,
    PROFILE_IRIS,
    PROFILE_JENKINS,
    PROFILE_LDAP,
    PROFILE_LOCALCI,
    PROFILE_SAML2,
    PROFILE_THEIA,
    ProfileFeature,
} from 'app/app.constants';

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
    styleUrl: './admin-feature-toggle.component.scss',
    imports: [FaIconComponent, TranslateDirective, ArtemisTranslatePipe, NgbTooltip, AdminTitleBarTitleDirective, AdminTitleBarActionsDirective],
})
export class AdminFeatureToggleComponent implements OnInit {
    private readonly featureToggleService = inject(FeatureToggleService);
    private readonly profileService = inject(ProfileService);
    private readonly translateService = inject(TranslateService);
    private readonly changeDetectorRef = inject(ChangeDetectorRef);
    private readonly destroyRef = inject(DestroyRef);

    /** Available feature toggles with their current state */
    readonly featureToggles = signal<FeatureToggleInfo[]>([]);

    /** Profile-based features with their current state */
    readonly profileFeatures = signal<ProfileFeatureInfo[]>([]);

    /** Module features with their current state */
    readonly moduleFeatures = signal<ModuleFeatureInfo[]>([]);

    /** Icons */
    protected readonly faExternalLinkAlt = faExternalLinkAlt;
    protected readonly faQuestionCircle = faQuestionCircle;

    /** Profiles to display (excluding internal profiles like dev, prod, test) */
    private readonly displayedProfiles: ProfileFeature[] = [
        PROFILE_IRIS,
        PROFILE_ATHENA,
        PROFILE_APOLLON,
        PROFILE_THEIA,
        PROFILE_LDAP,
        PROFILE_SAML2,
        PROFILE_LOCALCI,
        PROFILE_BUILDAGENT,
        PROFILE_AEOLUS,
        PROFILE_JENKINS,
    ];

    /** Module features to display */
    private readonly displayedModuleFeatures: ModuleFeature[] = [
        MODULE_FEATURE_ATLAS,
        MODULE_FEATURE_HYPERION,
        MODULE_FEATURE_EXAM,
        MODULE_FEATURE_PLAGIARISM,
        MODULE_FEATURE_TEXT,
        MODULE_FEATURE_MODELING,
        MODULE_FEATURE_FILEUPLOAD,
        MODULE_FEATURE_LECTURE,
        MODULE_FEATURE_TUTORIALGROUP,
        MODULE_FEATURE_NEBULA,
        MODULE_FEATURE_SHARING,
        MODULE_FEATURE_LTI,
        MODULE_FEATURE_PASSKEY,
    ];

    /** Documentation links for runtime feature toggles */
    private readonly documentationLinks: Partial<Record<FeatureToggle, string>> = {
        [FeatureToggle.ProgrammingExercises]: 'https://docs.artemis.tum.de/instructor/exercises/programming-exercise',
        [FeatureToggle.PlagiarismChecks]: 'https://docs.artemis.tum.de/instructor/plagiarism-check',
        [FeatureToggle.Exports]: 'https://docs.artemis.tum.de/instructor/exports',
        [FeatureToggle.LearningPaths]: 'https://docs.artemis.tum.de/instructor/adaptive-learning',
        [FeatureToggle.StandardizedCompetencies]: 'https://docs.artemis.tum.de/admin/adaptive-learning',
        [FeatureToggle.StudentCourseAnalyticsDashboard]: 'https://docs.artemis.tum.de/instructor/learning-analytics',
        [FeatureToggle.TutorSuggestions]: 'https://docs.artemis.tum.de/instructor/communication#tutor-suggestions',
        [FeatureToggle.AtlasML]: 'https://docs.artemis.tum.de/admin/artemis-intelligence',
        [FeatureToggle.AtlasAgent]: 'https://docs.artemis.tum.de/admin/artemis-intelligence',
        [FeatureToggle.Memiris]: 'https://docs.artemis.tum.de/admin/extensions-setup#iris--pyris-setup-guide',
        [FeatureToggle.LectureContentProcessing]: 'https://docs.artemis.tum.de/admin/extensions-setup#nebula-setup-guide',
    };

    /** Documentation links for profile-based features */
    private readonly profileDocumentationLinks: Partial<Record<ProfileFeature, string>> = {
        [PROFILE_IRIS]: 'https://docs.artemis.tum.de/admin/extensions-setup#iris--pyris-setup-guide',
        [PROFILE_ATHENA]: 'https://docs.artemis.tum.de/admin/extensions-setup#athena-service',
        [PROFILE_APOLLON]: 'https://docs.artemis.tum.de/instructor/exercises/modeling-exercise',
        [PROFILE_THEIA]: 'https://docs.artemis.tum.de/developer/setup#run-the-server-via-a-run-configuration-in-intellij',
        [PROFILE_LDAP]: 'https://docs.artemis.tum.de/admin/production-setup/security#ldap-authentication',
        [PROFILE_SAML2]: 'https://docs.artemis.tum.de/admin/saml2-login-registration',
        [PROFILE_LOCALCI]: 'https://docs.artemis.tum.de/developer/setup#integrated-code-lifecycle-setup',
        [PROFILE_BUILDAGENT]: 'https://docs.artemis.tum.de/developer/setup#integrated-code-lifecycle-setup',
        [PROFILE_AEOLUS]: 'https://docs.artemis.tum.de/developer/aeolus',
        [PROFILE_JENKINS]: 'https://docs.artemis.tum.de/developer/jenkins-localvc',
    };

    /** Documentation links for module features */
    private readonly moduleDocumentationLinks: Partial<Record<ModuleFeature, string>> = {
        [MODULE_FEATURE_ATLAS]: 'https://docs.artemis.tum.de/instructor/adaptive-learning',
        [MODULE_FEATURE_HYPERION]: 'https://docs.artemis.tum.de/admin/hyperion',
        [MODULE_FEATURE_EXAM]: 'https://docs.artemis.tum.de/instructor/exams/intro',
        [MODULE_FEATURE_PLAGIARISM]: 'https://docs.artemis.tum.de/instructor/plagiarism-check',
        [MODULE_FEATURE_TEXT]: 'https://docs.artemis.tum.de/instructor/exercises/textual-exercise',
        [MODULE_FEATURE_MODELING]: 'https://docs.artemis.tum.de/instructor/exercises/modeling-exercise',
        [MODULE_FEATURE_FILEUPLOAD]: 'https://docs.artemis.tum.de/instructor/exercises/file-upload-exercise',
        [MODULE_FEATURE_LECTURE]: 'https://docs.artemis.tum.de/instructor/lectures',
        [MODULE_FEATURE_TUTORIALGROUP]: 'https://docs.artemis.tum.de/instructor/tutorial-groups',
        [MODULE_FEATURE_NEBULA]: 'https://docs.artemis.tum.de/admin/extensions-setup#nebula-setup-guide',
        [MODULE_FEATURE_SHARING]: 'https://docs.artemis.tum.de/admin/extensions-setup#setup-guide-for-exchange-with-the-sharing-platform',
        [MODULE_FEATURE_LTI]: 'https://docs.artemis.tum.de/instructor/lti-configuration',
        [MODULE_FEATURE_PASSKEY]: 'https://docs.artemis.tum.de/admin/production-setup/security#passkey-authentication',
    };

    ngOnInit(): void {
        // Load runtime feature toggles
        this.featureToggleService
            .getFeatureToggles()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((activeToggles) => {
                this.featureToggles.set(
                    Object.values(FeatureToggle).map((feature) => ({
                        feature,
                        isActive: activeToggles.includes(feature),
                        documentationLink: this.documentationLinks[feature],
                    })),
                );
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

        this.translateService.onLangChange.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
            this.changeDetectorRef.markForCheck();
        });
    }

    onFeatureToggle(featureInfo: FeatureToggleInfo): void {
        const newState = !featureInfo.isActive;
        this.featureToggleService
            .setFeatureToggleState(featureInfo.feature, newState)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe(() => {
                this.featureToggles.update((toggles) => toggles.map((toggle) => (toggle.feature === featureInfo.feature ? { ...toggle, isActive: newState } : toggle)));
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
