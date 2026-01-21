import { AlertService } from 'app/shared/service/alert.service';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { faBan, faPlus, faSave } from '@fortawesome/free-solid-svg-icons';
import { LtiPlatformConfiguration } from 'app/lti/shared/entities/lti-configuration.model';
import { LtiConfigurationService } from 'app/core/admin/lti-configuration/lti-configuration.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { AdminTitleBarTitleDirective } from 'app/core/admin/shared/admin-title-bar-title.directive';

/**
 * Admin component for creating and editing LTI platform configurations.
 */
@Component({
    selector: 'jhi-edit-lti-configuration',
    templateUrl: './edit-lti-configuration.component.html',
    imports: [FormsModule, ReactiveFormsModule, TranslateDirective, HelpIconComponent, FaIconComponent, AdminTitleBarTitleDirective],
})
export class EditLtiConfigurationComponent implements OnInit {
    private readonly route = inject(ActivatedRoute);
    private readonly ltiConfigurationService = inject(LtiConfigurationService);
    private readonly router = inject(Router);
    private readonly alertService = inject(AlertService);

    platform: LtiPlatformConfiguration;
    platformConfigurationForm: FormGroup;

    /** Whether save is in progress */
    readonly isSaving = signal(false);

    /** Whether we're in edit mode (editing existing configuration) */
    readonly isEditMode = signal(false);

    /** Whether loading the configuration failed in edit mode */
    readonly loadFailed = signal(false);

    protected readonly faBan = faBan;
    protected readonly faSave = faSave;
    protected readonly faPlus = faPlus;

    /**
     * Gets the configuration for the course encoded in the route and prepares the form
     */
    ngOnInit() {
        // Always initialize the form first with empty values
        this.initializeForm();

        const platformId = this.route.snapshot.paramMap.get('platformId');
        if (platformId) {
            // Edit mode: load data and patch form values
            this.isEditMode.set(true);
            this.ltiConfigurationService.getLtiPlatformById(Number(platformId)).subscribe({
                next: (data) => {
                    this.platform = data;
                    this.patchFormValues();
                },
                error: (error) => {
                    this.loadFailed.set(true);
                    this.alertService.error(error);
                },
            });
        }
    }

    /**
     * Create or update lti platform configuration
     */
    save() {
        // If we're in edit mode but loading failed, don't allow save
        if (this.isEditMode() && this.loadFailed()) {
            this.alertService.error('artemisApp.lti.editConfiguration.loadError');
            return;
        }
        this.isSaving.set(true);
        const platformConfiguration = this.platformConfigurationForm.getRawValue();
        if (this.platform?.id) {
            this.updateLtiConfiguration(platformConfiguration);
        } else {
            this.addLtiConfiguration(platformConfiguration);
        }
    }

    /**
     * Update existing platform configuration
     */
    updateLtiConfiguration(platformConfiguration: any) {
        this.ltiConfigurationService
            .updateLtiPlatformConfiguration(platformConfiguration)
            .pipe(
                finalize(() => {
                    this.isSaving.set(false);
                }),
            )
            .subscribe({
                next: () => this.onSaveSuccess(),
                error: (error) => {
                    this.alertService.error(error);
                },
            });
    }

    /**
     * Create new platform configuration
     */
    addLtiConfiguration(platformConfiguration: any) {
        this.ltiConfigurationService
            .addLtiPlatformConfiguration(platformConfiguration)
            .pipe(
                finalize(() => {
                    this.isSaving.set(false);
                }),
            )
            .subscribe({
                next: () => this.onSaveSuccess(),
                error: (error) => {
                    this.alertService.error(error);
                },
            });
    }

    /**
     * Action on successful online course configuration or edit
     */
    private onSaveSuccess() {
        this.isSaving.set(false);
        this.navigateToLtiConfigurationPage();
    }

    private initializeForm() {
        this.platformConfigurationForm = new FormGroup({
            id: new FormControl(null),
            registrationId: new FormControl({ value: '', disabled: true }),
            originalUrl: new FormControl(''),
            customName: new FormControl(''),
            clientId: new FormControl(''),
            authorizationUri: new FormControl(''),
            tokenUri: new FormControl(''),
            jwkSetUri: new FormControl(''),
        });
    }

    private patchFormValues() {
        if (this.platform && this.platformConfigurationForm) {
            this.platformConfigurationForm.patchValue({
                id: this.platform.id,
                registrationId: this.platform.registrationId,
                originalUrl: this.platform.originalUrl ?? '',
                customName: this.platform.customName ?? '',
                clientId: this.platform.clientId,
                authorizationUri: this.platform.authorizationUri,
                tokenUri: this.platform.tokenUri,
                jwkSetUri: this.platform.jwkSetUri,
            });
        }
    }

    /**
     * Returns to the lti configuration page
     */
    navigateToLtiConfigurationPage() {
        this.router.navigate(['admin', 'lti-configuration']);
    }
}
