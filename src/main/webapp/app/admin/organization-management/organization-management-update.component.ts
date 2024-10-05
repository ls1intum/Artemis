import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Organization } from 'app/entities/organization.model';
import { OrganizationManagementService } from 'app/admin/organization-management/organization-management.service';
import { faBan, faSave } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-organization-management-update',
    templateUrl: './organization-management-update.component.html',
})
export class OrganizationManagementUpdateComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private organizationService = inject(OrganizationManagementService);

    organization: Organization;
    isSaving: boolean;

    // Icons
    faSave = faSave;
    faBan = faBan;

    /**
     * Enable subscriptions to retrieve the organization based on the activated route on init
     */
    ngOnInit() {
        this.isSaving = false;
        // create a new organization and only overwrite it if we fetch an organization to edit
        this.organization = new Organization();
        this.route.parent!.data.subscribe(({ organization }) => {
            if (organization) {
                const organizationId = organization.body ? organization.body.id : organization.id;
                this.organizationService.getOrganizationById(organizationId).subscribe((data) => {
                    this.organization = data;
                });
            }
        });
    }

    /**
     * Navigate to the previous page when the user cancels the update process
     */
    previousState() {
        window.history.back();
    }

    /**
     * Update or create user in the user management component
     */
    save() {
        this.isSaving = true;
        if (this.organization.id) {
            this.organizationService.update(this.organization).subscribe({
                next: () => this.onSaveSuccess(),
                error: () => this.onSaveError(),
            });
        } else {
            this.organizationService.add(this.organization).subscribe({
                next: () => this.onSaveSuccess(),
                error: () => this.onSaveError(),
            });
        }
    }

    /**
     * Set isSaving to false and navigate to previous page
     */
    private onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    /**
     * Set isSaving to false
     */
    private onSaveError() {
        this.isSaving = false;
    }
}
