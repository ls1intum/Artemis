import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Data, ParamMap, Router } from '@angular/router';
import { Organization } from 'app/entities/organization.model';
import { OrganizationManagementService } from 'app/admin/organization-management/organization-management.service';
import { JhiAlertService } from 'ng-jhipster';
import { User } from 'app/core/user/user.model';

@Component({
    selector: 'jhi-organization-management-detail',
    templateUrl: './organization-management-detail.component.html',
})
export class OrganizationManagementDetailComponent implements OnInit {
    organization: Organization;

    constructor(private organizationService: OrganizationManagementService, private alertService: JhiAlertService, private route: ActivatedRoute) {}

    /**
     * Retrieve the organization from the organization management activated route data {@link OrganizationMgmtResolve} subscription
     * and get the organization based on its id
     */
    ngOnInit() {
        this.route.data.subscribe(({ organization }) => {
            const organizationId = organization.body ? organization.body.id : organization.id;
            this.organizationService.getOrganizationByIdWithUsersAndCourses(organizationId).subscribe((organizationWithUserAndCourses) => {
                this.organization = organizationWithUserAndCourses;
            });
        });
    }

    /**
     * Returns the unique identifier for items in the collection
     * @param index of a user in the collection
     * @param item current user
     */
    trackIdentity(index: number, item: User) {
        return item.id;
    }
}
