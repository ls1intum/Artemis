import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { AlertService } from 'app/shared/service/alert.service';
import { ButtonComponent, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { faCheck, faExclamationTriangle, faSave } from '@fortawesome/free-solid-svg-icons';
import { ComponentCanDeactivate } from 'app/shared/guard/can-deactivate.model';
import { cloneDeep, isEqual } from 'lodash-es';
import { AccountService } from 'app/core/auth/account.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FormsModule } from '@angular/forms';
import { captureException } from '@sentry/angular';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import {
    IRIS_PIPELINE_VARIANTS,
    IrisCourseSettingsDTO,
    IrisCourseSettingsWithRateLimitDTO,
    IrisPipelineVariant,
    IrisRateLimitConfiguration,
} from 'app/iris/shared/entities/settings/iris-course-settings.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { CourseTitleBarTitleComponent } from 'app/core/course/shared/course-title-bar-title/course-title-bar-title.component';
import { CourseTitleBarTitleDirective } from 'app/core/course/shared/directives/course-title-bar-title.directive';

/**
 * Component for editing Iris course-level settings.
 * Extracts the courseId from the route and provides the settings form.
 */
@Component({
    selector: 'jhi-iris-settings-update',
    templateUrl: './iris-settings-update.component.html',
    imports: [ButtonComponent, TranslateDirective, ArtemisTranslatePipe, FormsModule, FaIconComponent, CourseTitleBarTitleComponent, CourseTitleBarTitleDirective],
})
export class IrisSettingsUpdateComponent implements OnInit, ComponentCanDeactivate {
    private route = inject(ActivatedRoute);
    private destroyRef = inject(DestroyRef);
    private irisSettingsService = inject(IrisSettingsService);
    private alertService = inject(AlertService);
    private accountService = inject(AccountService);

    public courseId?: number;

    // Current settings being edited (signals)
    readonly settings = signal<IrisCourseSettingsDTO | undefined>(undefined);
    public effectiveRateLimit?: IrisRateLimitConfiguration;
    public applicationDefaults?: IrisRateLimitConfiguration;

    // Original settings for dirty checking
    private readonly originalSettings = signal<IrisCourseSettingsDTO | undefined>(undefined);

    // Available variants
    public availableVariants: ReadonlyArray<IrisPipelineVariant> = IRIS_PIPELINE_VARIANTS;

    // Local form fields for rate limit (separate from settings to preserve null semantics)
    // These are always safe to bind in the template, and we reconstruct rateLimit on save
    readonly rateLimitRequests = signal<number | undefined>(undefined);
    readonly rateLimitTimeframeHours = signal<number | undefined>(undefined);

    // Original rate limit values for dirty checking
    private readonly originalRateLimitRequests = signal<number | undefined>(undefined);
    private readonly originalRateLimitTimeframeHours = signal<number | undefined>(undefined);

    // Status flags
    isLoading = false;
    isSaving = false;
    isAdmin: boolean;
    private readonly isAutoSaving = signal(false);

    // Validation state for rate limit (field-specific errors) - computed
    readonly rateLimitRequestsError = computed(() => {
        const hasRequests = this.hasNumericValue(this.rateLimitRequests());
        const hasTimeframe = this.hasNumericValue(this.rateLimitTimeframeHours());

        // Both empty = valid (use defaults)
        if (!hasRequests && !hasTimeframe) {
            return undefined;
        }

        // One filled, one empty = mark the empty field with error
        if (!hasRequests && hasTimeframe) {
            return 'artemisApp.iris.settings.rateLimitValidation.bothRequired';
        }

        // Both filled - validate values
        if (hasRequests && hasTimeframe && this.rateLimitRequests()! < 0) {
            return 'artemisApp.iris.settings.rateLimitValidation.requestsNonNegative';
        }

        return undefined;
    });

    readonly rateLimitTimeframeError = computed(() => {
        const hasRequests = this.hasNumericValue(this.rateLimitRequests());
        const hasTimeframe = this.hasNumericValue(this.rateLimitTimeframeHours());

        // Both empty = valid (use defaults)
        if (!hasRequests && !hasTimeframe) {
            return undefined;
        }

        // One filled, one empty = mark the empty field with error
        if (hasRequests && !hasTimeframe) {
            return 'artemisApp.iris.settings.rateLimitValidation.bothRequired';
        }

        // Both filled - validate values
        if (hasRequests && hasTimeframe && this.rateLimitTimeframeHours()! <= 0) {
            return 'artemisApp.iris.settings.rateLimitValidation.timeframePositive';
        }

        return undefined;
    });

    // Dirty state computed from signal values
    readonly isDirty = computed(() => {
        if (this.isAutoSaving()) return false;
        const normalizedSettings = this.normalizeSettingsForComparison(this.settings());
        const normalizedOriginal = this.normalizeSettingsForComparison(this.originalSettings());
        const settingsChanged = !isEqual(normalizedSettings, normalizedOriginal);
        const rateLimitChanged =
            this.normalizeEmpty(this.rateLimitRequests()) !== this.normalizeEmpty(this.originalRateLimitRequests()) ||
            this.normalizeEmpty(this.rateLimitTimeframeHours()) !== this.normalizeEmpty(this.originalRateLimitTimeframeHours());
        return settingsChanged || rateLimitChanged;
    });

    // Button types
    PRIMARY = ButtonType.PRIMARY;
    WARNING = ButtonType.WARNING;
    SUCCESS = ButtonType.SUCCESS;

    // Icons
    faSave = faSave;
    faCheck = faCheck;
    faExclamationTriangle = faExclamationTriangle;

    // Character limit for custom instructions
    readonly CUSTOM_INSTRUCTIONS_MAX_LENGTH = 2048;

    constructor() {
        this.isAdmin = this.accountService.isAdmin();
    }

    ngOnInit(): void {
        this.route.params.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
            this.courseId = Number(params['courseId']);
            this.loadSettings();
        });
    }

    /**
     * Check if the form is valid for saving
     */
    isFormValid(): boolean {
        return !this.rateLimitRequestsError() && !this.rateLimitTimeframeError();
    }

    /**
     * Normalize empty values (empty string, null/undefined) to undefined for comparison
     */
    private normalizeEmpty<T>(value: T | null | undefined): T | undefined {
        if (value === null || value === undefined || value === '') {
            return undefined;
        }
        return value;
    }

    /**
     * Checks if a number field has a value (not null, undefined, or empty string from Angular forms).
     * Note: Angular's ngModel can set empty string on number inputs when cleared, hence the string check.
     */
    private hasNumericValue(value: number | string | undefined): boolean {
        return value != null && value !== '';
    }

    /**
     * Create a normalized copy of settings for dirty comparison
     */
    private normalizeSettingsForComparison(settings?: IrisCourseSettingsDTO): IrisCourseSettingsDTO | undefined {
        if (!settings) {
            return undefined;
        }
        return {
            ...settings,
            customInstructions: this.normalizeEmpty(settings.customInstructions) as string | undefined,
        };
    }

    canDeactivateWarning?: string;

    canDeactivate(): boolean {
        return !this.isDirty();
    }

    /**
     * Load course settings from the server
     */
    loadSettings(): void {
        if (!this.courseId) {
            this.alertService.error('artemisApp.iris.settings.error.noCourseId');
            return;
        }

        this.isLoading = true;
        this.irisSettingsService.getCourseSettingsWithRateLimit(this.courseId).subscribe({
            next: (response) => {
                this.isLoading = false;
                if (!response) {
                    this.alertService.error('artemisApp.iris.settings.error.noSettings');
                    return;
                }
                this.settings.set(response.settings);
                // Extract rate limit fields for form binding
                this.rateLimitRequests.set(this.settings()?.rateLimit?.requests);
                this.rateLimitTimeframeHours.set(this.settings()?.rateLimit?.timeframeHours);
                // Store original values for dirty checking
                this.originalRateLimitRequests.set(this.rateLimitRequests());
                this.originalRateLimitTimeframeHours.set(this.rateLimitTimeframeHours());
                this.effectiveRateLimit = response.effectiveRateLimit;
                this.applicationDefaults = response.applicationRateLimitDefaults;
                this.originalSettings.set(cloneDeep(this.settings()));
            },
            error: (error) => {
                this.isLoading = false;
                captureException('Error loading Iris settings', error);
                this.alertService.error('artemisApp.iris.settings.error.load');
            },
        });
    }

    /**
     * Save the current settings to the server
     */
    saveSettings(): void {
        const currentSettings = this.settings();
        if (!this.courseId || !currentSettings) {
            return;
        }

        // Normalize empty strings to undefined before saving
        const settingsToSave: IrisCourseSettingsDTO = {
            ...currentSettings,
            customInstructions: this.normalizeEmpty(currentSettings.customInstructions) as string | undefined,
        };

        const originalSettingsValue = this.originalSettings();
        if (!this.isAdmin) {
            // Non-admins can only change enabled and customInstructions
            // Restore original variant and rate limits to prevent unauthorized changes
            if (originalSettingsValue) {
                settingsToSave.variant = originalSettingsValue.variant;
                settingsToSave.rateLimit = originalSettingsValue.rateLimit;
            }
        } else {
            // Admin: reconstruct rateLimit from form fields
            settingsToSave.rateLimit = this.buildRateLimitForSave();
        }

        this.isSaving = true;
        this.irisSettingsService.updateCourseSettings(this.courseId, settingsToSave).subscribe({
            next: (response: HttpResponse<IrisCourseSettingsWithRateLimitDTO>) => {
                this.isSaving = false;
                if (response.body) {
                    this.settings.set(response.body.settings);
                    // Update local form fields from saved response
                    this.rateLimitRequests.set(this.settings()?.rateLimit?.requests);
                    this.rateLimitTimeframeHours.set(this.settings()?.rateLimit?.timeframeHours);
                    // Reset original values for dirty checking
                    this.originalRateLimitRequests.set(this.rateLimitRequests());
                    this.originalRateLimitTimeframeHours.set(this.rateLimitTimeframeHours());
                    this.effectiveRateLimit = response.body.effectiveRateLimit;
                    this.applicationDefaults = response.body.applicationRateLimitDefaults;
                    this.originalSettings.set(cloneDeep(this.settings()));
                }
                this.alertService.success('artemisApp.iris.settings.success');
            },
            error: (error) => {
                this.isSaving = false;
                captureException('Error saving Iris settings', error);
                if (error.status === 400 && error.error && error.error.message) {
                    this.alertService.error(error.error.message);
                } else {
                    this.alertService.error('artemisApp.iris.settings.error.save');
                }
            },
        });
    }

    /**
     * Toggle the enabled state and auto-save
     */
    setEnabled(enabled: boolean): void {
        const currentSettings = this.settings();
        if (currentSettings && currentSettings.enabled !== enabled) {
            this.settings.set({ ...currentSettings, enabled });
            // Auto-save enabled/disabled changes immediately
            this.saveEnabledOnly(enabled);
        }
    }

    /**
     * Save only the enabled state without requiring manual save
     */
    private saveEnabledOnly(enabled: boolean): void {
        const originalSettingsValue = this.originalSettings();
        if (!this.courseId || !originalSettingsValue) {
            return;
        }

        // Prevent dirty flash during auto-save
        this.isAutoSaving.set(true);

        // Create settings object with only enabled changed from original
        const settingsToSave: IrisCourseSettingsDTO = {
            ...originalSettingsValue,
            enabled,
        };

        this.irisSettingsService.updateCourseSettings(this.courseId, settingsToSave).subscribe({
            next: (response: HttpResponse<IrisCourseSettingsWithRateLimitDTO>) => {
                if (response.body) {
                    // Update original settings to reflect the new enabled state
                    this.originalSettings.set(cloneDeep(response.body.settings));
                    this.settings.set(cloneDeep(response.body.settings));
                    // Reset rate limit tracking
                    this.rateLimitRequests.set(this.settings()?.rateLimit?.requests);
                    this.rateLimitTimeframeHours.set(this.settings()?.rateLimit?.timeframeHours);
                    this.originalRateLimitRequests.set(this.rateLimitRequests());
                    this.originalRateLimitTimeframeHours.set(this.rateLimitTimeframeHours());
                    this.effectiveRateLimit = response.body.effectiveRateLimit;
                    this.applicationDefaults = response.body.applicationRateLimitDefaults;
                }
                this.isAutoSaving.set(false);
            },
            error: (error) => {
                this.isAutoSaving.set(false);
                captureException('Error saving Iris enabled state', error);
                // Revert on error
                const currentSettings = this.settings();
                if (currentSettings) {
                    this.settings.set({ ...currentSettings, enabled: !enabled });
                }
            },
        });
    }

    /**
     * Get the character count for custom instructions
     */
    getCustomInstructionsLength(): number {
        return this.settings()?.customInstructions?.length || 0;
    }

    /**
     * Update custom instructions in the settings signal
     */
    updateCustomInstructions(value: string): void {
        const currentSettings = this.settings();
        if (currentSettings) {
            this.settings.set({ ...currentSettings, customInstructions: value });
        }
    }

    /**
     * Update variant in the settings signal
     */
    updateVariant(value: IrisPipelineVariant): void {
        const currentSettings = this.settings();
        if (currentSettings) {
            this.settings.set({ ...currentSettings, variant: value });
        }
    }

    /**
     * Builds the rateLimit object for saving, preserving null semantics:
     * - undefined means "use application defaults" (sent when both fields are empty)
     * - {requests: X, timeframeHours: Y} means "explicit override with values"
     */
    private buildRateLimitForSave(): IrisRateLimitConfiguration | undefined {
        const requests = this.rateLimitRequests();
        const timeframe = this.rateLimitTimeframeHours();
        const hasRequests = requests != null;
        const hasTimeframe = timeframe != null;

        // If both fields are empty, use application defaults (return undefined)
        // This allows admins to revert a course back to defaults by clearing both fields
        if (!hasRequests && !hasTimeframe) {
            return undefined;
        }

        // If any field has a value, return explicit override
        return {
            requests,
            timeframeHours: timeframe,
        };
    }

    /**
     * Returns the effective rate limit configuration based on current form state.
     * Updates live as the user types to provide immediate feedback.
     */
    get effectiveRateLimitPreview(): IrisRateLimitConfiguration | undefined {
        const requests = this.rateLimitRequests();
        const timeframe = this.rateLimitTimeframeHours();
        const hasRequests = this.hasNumericValue(requests);
        const hasTimeframe = this.hasNumericValue(timeframe);

        // If both fields are empty, show application defaults
        if (!hasRequests && !hasTimeframe) {
            return this.applicationDefaults;
        }

        // If form is incomplete/invalid, show what's entered so far merged with defaults
        // This gives visual feedback while typing
        return {
            requests: hasRequests ? requests : this.applicationDefaults?.requests,
            timeframeHours: hasTimeframe ? timeframe : this.applicationDefaults?.timeframeHours,
        };
    }

    /**
     * Checks if the effective rate limit is unlimited (both null/undefined)
     */
    get isEffectiveRateLimitUnlimited(): boolean {
        const preview = this.effectiveRateLimitPreview;
        return !preview || (preview.requests == null && preview.timeframeHours == null);
    }

    /**
     * Checks if the effective requests limit is set (not null/undefined)
     */
    get hasEffectiveRequestsLimit(): boolean {
        return this.effectiveRateLimitPreview?.requests != null;
    }

    /**
     * Checks if the effective timeframe is set (not null/undefined)
     */
    get hasEffectiveTimeframeLimit(): boolean {
        return this.effectiveRateLimitPreview?.timeframeHours != null;
    }
}
