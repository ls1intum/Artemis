import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Organization } from 'app/entities/organization.model';

@Component({
    selector: 'jhi-organization-management-update',
    templateUrl: './organization-management-update.component.html',
})
export class OrganizationManagementUpdateComponent implements OnInit {
    organization: Organization;

    constructor(private route: ActivatedRoute) {}

    /**
     * Enable subscriptions to retrieve the organization based on the activated route on init
     */
    ngOnInit() {
        // create a new organization and only overwrite it if we fetch an organization to edit
        this.organization = new Organization();
        this.route.parent!.data.subscribe(({ organization }) => {
            if (organization) {
                this.organization = organization.body ? organization.body : organization;
            }
        });
    }
}
