import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { EMPTY, Subject, catchError, finalize, switchMap } from 'rxjs';
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
    TumAetUiButtonDirective,
    TumAetUiConfirmDialogComponent,
    TumAetUiConfirmationService,
    TumAetUiListComponent,
    TumAetUiListItemDirective,
    TumAetUiMessageComponent,
    TumAetUiProgressSpinnerComponent,
    TumAetUiToggleSwitchComponent,
} from '@tumaet/ui-angular';

@Component({
    selector: 'jhi-science-settings',
    templateUrl: 'science-settings.component.html',
    styleUrls: ['../user-settings.scss'],
    providers: [TumAetUiConfirmationService],
    imports: [
        FormsModule,
        FaIconComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        TumAetUiButtonDirective,
        TumAetUiConfirmDialogComponent,
        TumAetUiListComponent,
        TumAetUiListItemDirective,
        TumAetUiMessageComponent,
        TumAetUiProgressSpinnerComponent,
        TumAetUiToggleSwitchComponent,
    ],
})
export class ScienceSettingsComponent implements OnInit {
    private readonly destroyRef = inject(DestroyRef);
    private readonly scienceSettingsService = inject(ScienceSettingsService);
    private readonly alertService = inject(AlertService);
    private readonly translateService = inject(TranslateService);
    private readonly featureToggleService = inject(FeatureToggleService);
    private readonly confirmationService = inject(TumAetUiConfirmationService);

    readonly consents = signal<ScienceCourseConsent[]>([]);
    /** True until this page's own response arrived, so nothing but the spinner shows before it. */
    readonly loading = signal(true);
    readonly scienceFeatureActive = signal(true);
    /** The courses whose decision is being stored. Their switch is locked, so two saves can never overtake each other. */
    readonly savingCourseIds = signal<ReadonlySet<number>>(new Set());

    /** Every load goes through here, so only the latest one answers and none answers after the page is gone. */
    private readonly loads = new Subject<boolean>();

    protected readonly faInfoCircle = faInfoCircle;
    protected readonly faTrash = faTrash;

    ngOnInit(): void {
        this.loads
            .pipe(
                switchMap((featureActive) =>
                    featureActive
                        ? this.scienceSettingsService.getConsents().pipe(
                              // Inside the switchMap, so a failed load does not end the stream and a later reload still works.
                              catchError((error) => {
                                  this.loading.set(false);
                                  this.alertService.error('error.unexpectedError', { error: error.message });
                                  return EMPTY;
                              }),
                          )
                        : EMPTY,
                ),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe((consents) => {
                this.consents.set(consents);
                this.loading.set(false);
            });

        this.featureToggleService
            .getFeatureToggleActive(FeatureToggle.Science)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((active) => {
                this.scienceFeatureActive.set(active);
                this.loadConsents();
            });
    }

    /** Loads the consents while the feature is on; switched off, it cancels a running load and shows the message. */
    private loadConsents(): void {
        const featureActive = this.scienceFeatureActive();
        this.loading.set(featureActive);
        this.loads.next(featureActive);
    }

    /**
     * Stores the decision the switch has just moved to.
     *
     * The new state comes from the switch rather than from inverting the row, so a row reloaded underneath
     * the click cannot send the opposite of what the student saw. The row is updated before the request, because the
     * switch keeps its own visual state and would otherwise keep showing a decision the server never stored. The switch
     * stays locked until the server answered, and then shows the decision it confirmed.
     *
     * @param consent the row that was toggled
     * @param active  the state the switch moved to
     */
    toggleConsent(consent: ScienceCourseConsent, active: boolean): void {
        const courseId = consent.courseId;
        if (this.savingCourseIds().has(courseId)) {
            return;
        }
        this.savingCourseIds.update((courseIds) => new Set(courseIds).add(courseId));
        this.applyConsentLocally(courseId, active);
        this.scienceSettingsService
            .saveConsentForCourse(courseId, active)
            .pipe(
                finalize(() =>
                    this.savingCourseIds.update((courseIds) => {
                        const remaining = new Set(courseIds);
                        remaining.delete(courseId);
                        return remaining;
                    }),
                ),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe({
                next: (stored) => {
                    this.applyConsentLocally(courseId, stored.active);
                    // A list load still running may have read the decision before this save committed, and would then
                    // overwrite it. Starting it again cancels that read, and the new one sees the stored decision.
                    if (this.loading()) {
                        this.loadConsents();
                    }
                    this.alertService.success('artemisApp.userSettings.saveSettingsSuccessAlert');
                },
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
            accept: () => this.confirmDeleteData(consent, courseTitle),
        });
    }

    private confirmDeleteData(consent: ScienceCourseConsent, courseTitle: string): void {
        this.scienceSettingsService
            .deleteScienceDataForCourse(consent.courseId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                // The deletion removes interaction events only, so the consent row itself survives; it is reloaded so the
                // page cannot keep showing state from before the deletion.
                next: () => {
                    this.alertService.success('artemisApp.userSettings.scienceSettingsPage.deleteDataSuccess', { courseTitle });
                    this.loadConsents();
                },
                error: (error) => this.alertService.error('error.unexpectedError', { error: error.message }),
            });
    }
}
