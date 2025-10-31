import { Component, DoCheck, Input, OnInit, inject } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/shared/service/alert.service';
import { ButtonComponent, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { faRotate, faSave } from '@fortawesome/free-solid-svg-icons';
import { ComponentCanDeactivate } from 'app/shared/guard/can-deactivate.model';
import { cloneDeep, isEqual } from 'lodash-es';
import { AccountService } from 'app/core/auth/account.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FormsModule } from '@angular/forms';
import { captureException } from '@sentry/angular';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { CourseIrisSettingsDTO, IrisCourseSettingsDTO, IrisPipelineVariant, IrisRateLimitConfiguration } from 'app/iris/shared/entities/settings/iris-course-settings.model';
/**
 * Component for editing Iris course-level settings.
 * Replaces the legacy three-tier (Global → Course → Exercise) settings system
 * with a unified course-level configuration.
 */
@Component({
    selector: 'jhi-iris-settings-update',
    templateUrl: './iris-settings-update.component.html',
    imports: [ButtonComponent, TranslateDirective, ArtemisTranslatePipe, FormsModule],
})
export class IrisSettingsUpdateComponent implements OnInit, DoCheck, ComponentCanDeactivate {
    private irisSettingsService = inject(IrisSettingsService);
    private alertService = inject(AlertService);
    private accountService = inject(AccountService);

    @Input()
    public courseId!: number;

    // Current settings being edited
    public settings?: IrisCourseSettingsDTO;
    public effectiveRateLimit?: IrisRateLimitConfiguration;
    public applicationDefaults?: IrisRateLimitConfiguration;

    // Original settings for dirty checking
    private originalSettings?: IrisCourseSettingsDTO;

    // Available variants
    public availableVariants: IrisPipelineVariant[] = [];

    // Status flags
    isLoading = false;
    isSaving = false;
    isDirty = false;
    isAdmin: boolean;

    // Button types
    PRIMARY = ButtonType.PRIMARY;
    WARNING = ButtonType.WARNING;
    SUCCESS = ButtonType.SUCCESS;

    // Icons
    faSave = faSave;
    faRotate = faRotate;

    // Character limit for custom instructions
    readonly CUSTOM_INSTRUCTIONS_MAX_LENGTH = 2048;

    constructor() {
        this.isAdmin = this.accountService.isAdmin();
    }

    ngOnInit(): void {
        this.loadSettings();
        this.loadVariants();
    }

    ngDoCheck(): void {
        if (!isEqual(this.settings, this.originalSettings)) {
            this.isDirty = true;
        }
    }

    canDeactivateWarning?: string;

    canDeactivate(): boolean {
        return !this.isDirty;
    }

    /**
     * Load course settings from the backend
     */
    loadSettings(): void {
        if (!this.courseId) {
            this.alertService.error('artemisApp.iris.settings.error.noCourseId');
            return;
        }

        this.isLoading = true;
        this.irisSettingsService.getCourseSettings(this.courseId).subscribe({
            next: (response) => {
                this.isLoading = false;
                if (!response) {
                    this.alertService.error('artemisApp.iris.settings.error.noSettings');
                    return;
                }
                this.settings = response.settings;
                this.effectiveRateLimit = response.effectiveRateLimit;
                this.applicationDefaults = response.applicationRateLimitDefaults;
                this.originalSettings = cloneDeep(response.settings);
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
     * Load available pipeline variants
     */
    loadVariants(): void {
        this.irisSettingsService.getVariants().subscribe({
            next: (variants) => {
                this.availableVariants = variants;
            },
            error: (error) => {
                captureException('Error loading Iris variants', error);
            },
        });
    }

    /**
     * Save the current settings to the backend
     */
    saveSettings(): void {
        if (!this.courseId || !this.settings) {
            return;
        }

        // Validate admin-only fields
        if (!this.isAdmin) {
            // Non-admins can only change enabled and customInstructions
            // Restore original variant and rate limits to prevent unauthorized changes
            if (this.originalSettings) {
                this.settings.variant = this.originalSettings.variant;
                this.settings.rateLimit = this.originalSettings.rateLimit;
            }
        }

        this.isSaving = true;
        this.irisSettingsService.updateCourseSettings(this.courseId, this.settings).subscribe({
            next: (response: HttpResponse<CourseIrisSettingsDTO>) => {
                this.isSaving = false;
                this.isDirty = false;
                if (response.body) {
                    this.settings = response.body.settings;
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
     * Toggle the enabled state
     */
    setEnabled(enabled: boolean): void {
        if (this.settings) {
            this.settings.enabled = enabled;
        }
    }

    /**
     * Get the character count for custom instructions
     */
    getCustomInstructionsLength(): number {
        return this.settings?.customInstructions?.length || 0;
    }
}
