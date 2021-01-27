import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Organization } from 'app/entities/organization.model';

@Component({
    selector: 'jhi-organization-management-detail',
    templateUrl: './organization-management-detail.component.html',
})
export class OrganizationManagementDetailComponent implements OnInit {
    organization: Organization;

    constructor(private route: ActivatedRoute) {}

    /**
     * Retrieve the organization from the organization management activated route data {@link OrganizationMgmtResolve} subscription
     * and get the organization based on its id
     */
    ngOnInit() {
        this.route.data.subscribe(({ organization }) => {
            this.organization = organization.body ? organization.body : organization;
        });
    }
}
