import { AlertService } from 'app/core/util/alert.service';
import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs';
import { FormControl, FormGroup } from '@angular/forms';
import { faBan, faPlus, faSave } from '@fortawesome/free-solid-svg-icons';
import { LtiPlatformConfiguration } from 'app/admin/lti-configuration/lti-configuration.model';
import { LtiConfigurationService } from 'app/admin/lti-configuration/lti-configuration.service';

@Component({
    selector: 'jhi-edit-lti-configuration',
    templateUrl: './edit-lti-configuration.component.html',
})
export class EditLtiConfigurationComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private ltiConfigurationService = inject(LtiConfigurationService);
    private router = inject(Router);
    private alertService = inject(AlertService);

    platform: LtiPlatformConfiguration;
    platformConfigurationForm: FormGroup;

    isSaving = false;

    // Icons
    faBan = faBan;
    faSave = faSave;
    faPlus = faPlus;

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
        this.isSaving = true;
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
                    this.isSaving = false;
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
                    this.isSaving = false;
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
        this.isSaving = false;
        this.navigateToLtiConfigurationPage();
    }

    private initializeForm() {
        this.platformConfigurationForm = new FormGroup({
            id: new FormControl(this.platform?.id),
            registrationId: new FormControl(this.platform?.registrationId),
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
