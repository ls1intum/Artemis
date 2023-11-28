import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs';
import { FormControl, FormGroup } from '@angular/forms';
import { faBan, faSave } from '@fortawesome/free-solid-svg-icons';
import { LtiPlatformConfiguration } from 'app/admin/lti-configuration/lti-configuration.model';
import { LtiConfigurationService } from 'app/admin/lti-configuration/lti-configuration.service';
import { HttpClient } from '@angular/common/http';

@Component({
    selector: 'jhi-edit-lti-configuration',
    templateUrl: './edit-lti-configuration.component.html',
})
export class EditLtiConfigurationComponent implements OnInit {
    platform: LtiPlatformConfiguration;
    platformConfigurationForm: FormGroup;

    isSaving = false;

    // Icons
    faBan = faBan;
    faSave = faSave;

    constructor(
        private route: ActivatedRoute,
        private ltiConfigurationService: LtiConfigurationService,
        private router: Router,
        private http: HttpClient,
    ) {}

    /**
     * Gets the configuration for the course encoded in the route and prepares the form
     */
    ngOnInit() {
        const platformId = this.route.snapshot.paramMap.get('platformId');
        this.http.get<LtiPlatformConfiguration>(`api/admin/lti-platform/${platformId}`).subscribe({
            next: (data) => {
                this.platform = data;

                this.platformConfigurationForm = new FormGroup({
                    id: new FormControl(this.platform?.id),
                    registrationId: new FormControl(this.platform?.registrationId),
                    customName: new FormControl(this.platform?.customName),
                    clientId: new FormControl(this.platform?.clientId),
                    authorizationUri: new FormControl(this.platform?.authorizationUri),
                    tokenUri: new FormControl(this.platform?.tokenUri),
                    jwkSetUri: new FormControl(this.platform?.jwkSetUri),
                });
            },
            error: (error) => {
                console.error(error);
            },
        });
    }

    /**
     * Save the changes to the online course configuration
     */
    save() {
        this.isSaving = true;
        const platformConfiguration = this.platformConfigurationForm.getRawValue();
        this.ltiConfigurationService
            .updateLtiPlatformConfiguration(platformConfiguration)
            .pipe(
                finalize(() => {
                    this.isSaving = false;
                }),
            )
            .subscribe({
                next: () => this.onSaveSuccess(),
            });
    }

    /**
     * Action on successful online course configuration or edit
     */
    private onSaveSuccess() {
        this.isSaving = false;
        this.navigateToLtiConfigurationPage();
    }

    /**
     * Returns to the lti configuration page
     */
    navigateToLtiConfigurationPage() {
        this.router.navigate(['admin', 'lti-configuration']);
    }
}
