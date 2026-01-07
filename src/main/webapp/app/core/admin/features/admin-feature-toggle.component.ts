import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateService } from '@ngx-translate/core';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { faExternalLinkAlt, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';

type FeatureToggleInfo = {
    feature: FeatureToggle;
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
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FaIconComponent, TranslateDirective, ArtemisTranslatePipe, NgbTooltip],
})
export class AdminFeatureToggleComponent implements OnInit {
    private readonly featureToggleService = inject(FeatureToggleService);
    private readonly translateService = inject(TranslateService);
    private readonly changeDetectorRef = inject(ChangeDetectorRef);
    private readonly destroyRef = inject(DestroyRef);

    /** Available feature toggles with their current state */
    readonly featureToggles = signal<FeatureToggleInfo[]>([]);

    /** Icons */
    protected readonly faExternalLinkAlt = faExternalLinkAlt;
    protected readonly faQuestionCircle = faQuestionCircle;

    /** Documentation links for features */
    private readonly documentationLinks: Partial<Record<FeatureToggle, string>> = {
        [FeatureToggle.ProgrammingExercises]: 'https://docs.artemis.cit.tum.de/user/exercises/programming/',
        [FeatureToggle.PlagiarismChecks]: 'https://docs.artemis.cit.tum.de/user/plagiarism-check/',
        [FeatureToggle.Exports]: 'https://docs.artemis.cit.tum.de/user/exports/',
        [FeatureToggle.LearningPaths]: 'https://docs.artemis.cit.tum.de/user/adaptive-learning/',
        [FeatureToggle.StandardizedCompetencies]: 'https://docs.artemis.cit.tum.de/user/adaptive-learning/',
        [FeatureToggle.StudentCourseAnalyticsDashboard]: 'https://docs.artemis.cit.tum.de/user/learning-analytics/',
        [FeatureToggle.TutorSuggestions]: 'https://docs.artemis.cit.tum.de/admin/setup/athena/',
        [FeatureToggle.AtlasML]: 'https://docs.artemis.cit.tum.de/user/adaptive-learning/',
        [FeatureToggle.AtlasAgent]: 'https://docs.artemis.cit.tum.de/admin/setup/pyris/',
        [FeatureToggle.Memiris]: 'https://docs.artemis.cit.tum.de/admin/setup/pyris/',
        [FeatureToggle.LectureContentProcessing]: 'https://docs.artemis.cit.tum.de/admin/setup/nebula/',
    };

    ngOnInit(): void {
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
     * Get the translation key for a feature's name
     */
    getFeatureNameKey(feature: FeatureToggle): string {
        return `artemisApp.featureToggle.features.${feature}.name`;
    }

    /**
     * Get the translation key for a feature's description
     */
    getFeatureDescriptionKey(feature: FeatureToggle): string {
        return `artemisApp.featureToggle.features.${feature}.description`;
    }

    /**
     * Get the translation key for a feature's disable warning
     */
    getFeatureWarningKey(feature: FeatureToggle): string {
        return `artemisApp.featureToggle.features.${feature}.disableWarning`;
    }
}
