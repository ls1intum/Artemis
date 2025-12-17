import { Component, DoCheck, OnInit, inject } from '@angular/core';
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
export class IrisSettingsUpdateComponent implements OnInit, DoCheck, ComponentCanDeactivate {
    private route = inject(ActivatedRoute);
    private irisSettingsService = inject(IrisSettingsService);
    private alertService = inject(AlertService);
    private accountService = inject(AccountService);

    public courseId?: number;

    // Current settings being edited
    public settings?: IrisCourseSettingsDTO;
    public effectiveRateLimit?: IrisRateLimitConfiguration;
    public applicationDefaults?: IrisRateLimitConfiguration;

    // Original settings for dirty checking
    private originalSettings?: IrisCourseSettingsDTO;

    // Available variants
    public availableVariants: ReadonlyArray<IrisPipelineVariant> = IRIS_PIPELINE_VARIANTS;

    // Local form fields for rate limit (separate from settings to preserve null semantics)
    // These are always safe to bind in the template, and we reconstruct rateLimit on save
    public rateLimitRequests?: number;
    public rateLimitTimeframeHours?: number;

    // Original rate limit values for dirty checking
    private originalRateLimitRequests?: number;
    private originalRateLimitTimeframeHours?: number;

    // Status flags
    isLoading = false;
    isSaving = false;
    isDirty = false;
    isAdmin: boolean;
    private isAutoSaving = false; // Prevents dirty flash during enable/disable auto-save

    // Validation state for rate limit (field-specific errors)
    rateLimitRequestsError?: string;
    rateLimitTimeframeError?: string;

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
        this.route.params.subscribe((params) => {
            this.courseId = Number(params['courseId']);
            this.loadSettings();
        });
    }

    ngDoCheck(): void {
        // Skip dirty check during auto-save to prevent badge/button flash
        if (this.isAutoSaving) {
            return;
        }
        // Normalize settings for comparison (treat empty string same as undefined/null)
        const normalizedSettings = this.normalizeSettingsForComparison(this.settings);
        const normalizedOriginal = this.normalizeSettingsForComparison(this.originalSettings);
        const settingsChanged = !isEqual(normalizedSettings, normalizedOriginal);
        const rateLimitChanged =
            this.normalizeEmpty(this.rateLimitRequests) !== this.normalizeEmpty(this.originalRateLimitRequests) ||
            this.normalizeEmpty(this.rateLimitTimeframeHours) !== this.normalizeEmpty(this.originalRateLimitTimeframeHours);
        this.isDirty = settingsChanged || rateLimitChanged;

        // Validate rate limit fields
        this.validateRateLimit();
    }

    /**
     * Validates the rate limit fields.
     * Rules:
     * - Both fields must be empty (use defaults) OR both must be filled
     * - If filled: requests >= 0, timeframe > 0 (no zero timeframe allowed)
     */
    private validateRateLimit(): void {
        const hasRequests = this.hasNumericValue(this.rateLimitRequests);
        const hasTimeframe = this.hasNumericValue(this.rateLimitTimeframeHours);

        // Reset errors
        this.rateLimitRequestsError = undefined;
        this.rateLimitTimeframeError = undefined;

        // Both empty = valid (use defaults)
        if (!hasRequests && !hasTimeframe) {
            return;
        }

        // One filled, one empty = mark the empty field with error
        if (hasRequests && !hasTimeframe) {
            this.rateLimitTimeframeError = 'artemisApp.iris.settings.rateLimitValidation.bothRequired';
            return;
        }
        if (!hasRequests && hasTimeframe) {
            this.rateLimitRequestsError = 'artemisApp.iris.settings.rateLimitValidation.bothRequired';
            return;
        }

        // Both filled - validate values
        if (this.rateLimitRequests! < 0) {
            this.rateLimitRequestsError = 'artemisApp.iris.settings.rateLimitValidation.requestsNonNegative';
        }

        if (this.rateLimitTimeframeHours! <= 0) {
            this.rateLimitTimeframeError = 'artemisApp.iris.settings.rateLimitValidation.timeframePositive';
        }
    }

    /**
     * Check if the form is valid for saving
     */
    isFormValid(): boolean {
        return !this.rateLimitRequestsError && !this.rateLimitTimeframeError;
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
     * Checks if a number field has a value (not null, undefined, or empty string from Angular forms)
     */
    private hasNumericValue(value: number | undefined): boolean {
        return value != null && (value as unknown) !== '';
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
        return !this.isDirty;
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
                this.settings = response.settings;
                // Extract rate limit fields for form binding
                this.rateLimitRequests = this.settings.rateLimit?.requests;
                this.rateLimitTimeframeHours = this.settings.rateLimit?.timeframeHours;
                // Store original values for dirty checking
                this.originalRateLimitRequests = this.rateLimitRequests;
                this.originalRateLimitTimeframeHours = this.rateLimitTimeframeHours;
                this.effectiveRateLimit = response.effectiveRateLimit;
                this.applicationDefaults = response.applicationRateLimitDefaults;
                this.originalSettings = cloneDeep(this.settings);
                this.isDirty = false;
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
        if (!this.courseId || !this.settings) {
            return;
        }

        // Normalize empty strings to undefined before saving
        const settingsToSave: IrisCourseSettingsDTO = {
            ...this.settings,
            customInstructions: this.normalizeEmpty(this.settings.customInstructions) as string | undefined,
        };

        if (!this.isAdmin) {
            // Non-admins can only change enabled and customInstructions
            // Restore original variant and rate limits to prevent unauthorized changes
            if (this.originalSettings) {
                settingsToSave.variant = this.originalSettings.variant;
                settingsToSave.rateLimit = this.originalSettings.rateLimit;
            }
        } else {
            // Admin: reconstruct rateLimit from form fields
            settingsToSave.rateLimit = this.buildRateLimitForSave();
        }

        this.isSaving = true;
        this.irisSettingsService.updateCourseSettings(this.courseId, settingsToSave).subscribe({
            next: (response: HttpResponse<IrisCourseSettingsWithRateLimitDTO>) => {
                this.isSaving = false;
                this.isDirty = false;
                if (response.body) {
                    this.settings = response.body.settings;
                    // Update local form fields from saved response
                    this.rateLimitRequests = this.settings.rateLimit?.requests;
                    this.rateLimitTimeframeHours = this.settings.rateLimit?.timeframeHours;
                    // Reset original values for dirty checking
                    this.originalRateLimitRequests = this.rateLimitRequests;
                    this.originalRateLimitTimeframeHours = this.rateLimitTimeframeHours;
                    this.effectiveRateLimit = response.body.effectiveRateLimit;
                    this.applicationDefaults = response.body.applicationRateLimitDefaults;
                    this.originalSettings = cloneDeep(this.settings);
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
        if (this.settings && this.settings.enabled !== enabled) {
            this.settings.enabled = enabled;
            // Auto-save enabled/disabled changes immediately
            this.saveEnabledOnly(enabled);
        }
    }

    /**
     * Save only the enabled state without requiring manual save
     */
    private saveEnabledOnly(enabled: boolean): void {
        if (!this.courseId || !this.originalSettings) {
            return;
        }

        // Prevent dirty flash during auto-save
        this.isAutoSaving = true;

        // Create settings object with only enabled changed from original
        const settingsToSave: IrisCourseSettingsDTO = {
            ...this.originalSettings,
            enabled,
        };

        this.irisSettingsService.updateCourseSettings(this.courseId, settingsToSave).subscribe({
            next: (response: HttpResponse<IrisCourseSettingsWithRateLimitDTO>) => {
                if (response.body) {
                    // Update original settings to reflect the new enabled state
                    this.originalSettings = cloneDeep(response.body.settings);
                    this.settings = cloneDeep(response.body.settings);
                    // Reset rate limit tracking
                    this.rateLimitRequests = this.settings.rateLimit?.requests;
                    this.rateLimitTimeframeHours = this.settings.rateLimit?.timeframeHours;
                    this.originalRateLimitRequests = this.rateLimitRequests;
                    this.originalRateLimitTimeframeHours = this.rateLimitTimeframeHours;
                    this.effectiveRateLimit = response.body.effectiveRateLimit;
                    this.applicationDefaults = response.body.applicationRateLimitDefaults;
                }
                this.isAutoSaving = false;
            },
            error: (error) => {
                this.isAutoSaving = false;
                captureException('Error saving Iris enabled state', error);
                // Revert on error
                if (this.settings) {
                    this.settings.enabled = !enabled;
                }
            },
        });
    }

    /**
     * Get the character count for custom instructions
     */
    getCustomInstructionsLength(): number {
        return this.settings?.customInstructions?.length || 0;
    }

    /**
     * Builds the rateLimit object for saving, preserving null semantics:
     * - undefined means "use application defaults" (sent when both fields are empty)
     * - {requests: X, timeframeHours: Y} means "explicit override with values"
     */
    private buildRateLimitForSave(): IrisRateLimitConfiguration | undefined {
        const hasRequests = this.rateLimitRequests != null;
        const hasTimeframe = this.rateLimitTimeframeHours != null;

        // If both fields are empty, use application defaults (return undefined)
        // This allows admins to revert a course back to defaults by clearing both fields
        if (!hasRequests && !hasTimeframe) {
            return undefined;
        }

        // If any field has a value, return explicit override
        return {
            requests: this.rateLimitRequests,
            timeframeHours: this.rateLimitTimeframeHours,
        };
    }

    /**
     * Returns the effective rate limit configuration based on current form state.
     * Updates live as the user types to provide immediate feedback.
     */
    get effectiveRateLimitPreview(): IrisRateLimitConfiguration | undefined {
        const hasRequests = this.hasNumericValue(this.rateLimitRequests);
        const hasTimeframe = this.hasNumericValue(this.rateLimitTimeframeHours);

        // If both fields are empty, show application defaults
        if (!hasRequests && !hasTimeframe) {
            return this.applicationDefaults;
        }

        // If form is incomplete/invalid, show what's entered so far merged with defaults
        // This gives visual feedback while typing
        return {
            requests: hasRequests ? this.rateLimitRequests : this.applicationDefaults?.requests,
            timeframeHours: hasTimeframe ? this.rateLimitTimeframeHours : this.applicationDefaults?.timeframeHours,
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
