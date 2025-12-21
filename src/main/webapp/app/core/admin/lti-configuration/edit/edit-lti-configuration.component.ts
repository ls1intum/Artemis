import { AlertService } from 'app/shared/service/alert.service';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { faBan, faPlus, faSave } from '@fortawesome/free-solid-svg-icons';
import { LtiPlatformConfiguration } from 'app/lti/shared/entities/lti-configuration.model';
import { LtiConfigurationService } from 'app/core/admin/lti-configuration/lti-configuration.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

/**
 * Admin component for creating and editing LTI platform configurations.
 */
@Component({
    selector: 'jhi-edit-lti-configuration',
    templateUrl: './edit-lti-configuration.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FormsModule, ReactiveFormsModule, TranslateDirective, HelpIconComponent, FaIconComponent],
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

    protected readonly faBan = faBan;
    protected readonly faSave = faSave;
    protected readonly faPlus = faPlus;

    /**
     * Gets the configuration for the course encoded in the route and prepares the form
     */
    ngOnInit() {
        const platformId = this.route.snapshot.paramMap.get('platformId');
        if (platformId) {
            this.ltiConfigurationService.getLtiPlatformById(Number(platformId)).subscribe({
                next: (data) => {
                    this.platform = data;
                    this.initializeForm();
                },
                error: (error) => {
                    this.alertService.error(error);
                },
            });
        }
        this.initializeForm();
    }

    /**
     * Create or update lti platform configuration
     */
    save() {
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
            id: new FormControl(this.platform?.id),
            registrationId: new FormControl({ value: this.platform?.registrationId, disabled: true }),
            originalUrl: new FormControl(this.platform?.originalUrl),
            customName: new FormControl(this.platform?.customName),
            clientId: new FormControl(this.platform?.clientId),
            authorizationUri: new FormControl(this.platform?.authorizationUri),
            tokenUri: new FormControl(this.platform?.tokenUri),
            jwkSetUri: new FormControl(this.platform?.jwkSetUri),
        });
    }

    /**
     * Returns to the lti configuration page
     */
    navigateToLtiConfigurationPage() {
        this.router.navigate(['admin', 'lti-configuration']);
    }
}
