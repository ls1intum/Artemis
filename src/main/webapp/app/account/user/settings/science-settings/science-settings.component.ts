import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { faInfoCircle, faTrash, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/foundation/service/alert.service';
import { ScienceCourseConsent, ScienceSettingsService } from 'app/account/user/settings/science-settings/science-settings.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FeatureToggle, FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import {
    TumUiButtonDirective,
    TumUiConfirmDialogComponent,
    TumUiConfirmationService,
    TumUiListComponent,
    TumUiListItemDirective,
    TumUiMessageComponent,
    TumUiToggleSwitchComponent,
} from '@tumaet/ui-angular';

@Component({
    selector: 'jhi-science-settings',
    templateUrl: 'science-settings.component.html',
    styleUrls: ['../user-settings.scss'],
    providers: [TumUiConfirmationService],
    imports: [
        FormsModule,
        FaIconComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        TumUiButtonDirective,
        TumUiConfirmDialogComponent,
        TumUiListComponent,
        TumUiListItemDirective,
        TumUiMessageComponent,
        TumUiToggleSwitchComponent,
    ],
})
export class ScienceSettingsComponent implements OnInit {
    private readonly destroyRef = inject(DestroyRef);
    private readonly scienceSettingsService = inject(ScienceSettingsService);
    private readonly alertService = inject(AlertService);
    private readonly translateService = inject(TranslateService);
    private readonly featureToggleService = inject(FeatureToggleService);
    private readonly confirmationService = inject(TumUiConfirmationService);

    readonly consents = signal<ScienceCourseConsent[]>([]);
    readonly loading = signal(false);
    readonly scienceFeatureActive = signal(true);

    protected readonly faInfoCircle = faInfoCircle;
    protected readonly faTrash = faTrash;

    ngOnInit(): void {
        // Subscribed once rather than per feature-toggle emission: the updates stream is a ReplaySubject that never
        // completes, so re-subscribing on every push would accumulate live subscribers for the life of the page.
        this.scienceSettingsService
            .getScienceSettingsUpdates()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((consents) => this.consents.set(consents));

        this.featureToggleService
            .getFeatureToggleActive(FeatureToggle.Science)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((active) => {
                this.scienceFeatureActive.set(active);
                if (active) {
                    this.loadConsents();
                } else {
                    this.loading.set(false);
                }
            });
    }

    private loadConsents(): void {
        this.loading.set(true);
        this.scienceSettingsService
            .refreshScienceSettings()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: () => this.loading.set(false),
                error: (error) => {
                    this.loading.set(false);
                    this.alertService.error('error.unexpectedError', { error: error.message });
                },
            });
    }

    /**
     * Stores the decision the switch has just moved to.
     *
     * The new state comes from the switch rather than from inverting the row, so a row the cache refreshed underneath
     * the click cannot send the opposite of what the student saw. The row is updated before the request, because the
     * switch keeps its own visual state and would otherwise keep showing a decision the server never stored.
     *
     * @param consent the row that was toggled
     * @param active  the state the switch moved to
     */
    toggleConsent(consent: ScienceCourseConsent, active: boolean): void {
        this.applyConsentLocally(consent.courseId, active);
        this.scienceSettingsService.saveConsentForCourse(consent.courseId, active).subscribe({
            next: () => this.alertService.success('artemisApp.userSettings.saveSettingsSuccessAlert'),
            error: (error) => {
                // Re-read rather than put the row back: a withdrawal stores the decision before recording it, so a
                // failure can still have changed the stored state, and guessing at it would show consent the server
                // does not have.
                this.loadConsents();
                this.alertService.error('error.unexpectedError', { error: error.message });
            },
        });
    }

    private applyConsentLocally(courseId: number, active?: boolean): void {
        this.consents.update((consents) => consents.map((consent) => (consent.courseId === courseId ? cloneWith(consent, { active }) : consent)));
    }

    deleteData(consent: ScienceCourseConsent): void {
        const courseTitle = consent.courseTitle ?? this.translateService.instant('artemisApp.userSettings.scienceSettingsPage.thisCourse');
        this.confirmationService.confirm({
            header: this.translateService.instant('artemisApp.userSettings.scienceSettingsPage.deleteData', { courseTitle }),
            message: this.translateService.instant('artemisApp.userSettings.scienceSettingsPage.deleteDataQuestion', { courseTitle }),
            acceptLabel: this.translateService.instant('entity.action.delete'),
            rejectLabel: this.translateService.instant('entity.action.cancel'),
            acceptSeverity: 'danger',
            icon: faTriangleExclamation,
            accept: () => this.confirmDeleteData(consent),
        });
    }

    private confirmDeleteData(consent: ScienceCourseConsent): void {
        this.scienceSettingsService.deleteScienceDataForCourse(consent.courseId).subscribe({
            // The deletion removes interaction events only, so the consent row itself survives; it is reloaded so the
            // page cannot keep showing state from before the deletion.
            next: () => {
                this.alertService.success('artemisApp.userSettings.saveSettingsSuccessAlert');
                this.loadConsents();
            },
            error: (error) => this.alertService.error('error.unexpectedError', { error: error.message }),
        });
    }
}
