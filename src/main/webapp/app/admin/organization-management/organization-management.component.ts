import { Component, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Organization } from 'app/entities/organization.model';
import { UserService } from 'app/core/user/user.service';
import { OrganizationManagementService } from 'app/admin/organization-management/organization-management.service';
import { JhiAlertService } from 'ng-jhipster';

@Component({
    selector: 'jhi-organization-management',
    templateUrl: './organization-management.component.html',
})
export class OrganizationManagementComponent implements OnInit {
    organizations: Organization[];

    constructor(private organizationService: OrganizationManagementService, private userService: UserService, private alertService: JhiAlertService) {}

    ngOnInit(): void {
        this.organizationService.getOrganizations().subscribe((organizations) => {
            this.organizations = organizations;
            this.organizationService.getNumberOfUsersAndCoursesOfOrganizations().subscribe((data) => {
                data = Object.values(data);
                for (let i = 0; i < data.length; i++) {
                    this.organizations[i].numberOfUsers = data[i].users;
                    this.organizations[i].numberOfCourses = data[i].courses;
                }
            });
        });
    }

    deleteOrganization(organizationId: number) {
        this.organizationService.deleteOrganization(organizationId).subscribe(
            () => {
                this.alertService.success('Organization has been removed successfully');
            },
            (error: HttpErrorResponse) => {
                this.alertService.error('An error occurred while removing the organization: ' + error.message);
            },
        );
    }

    /**
     * Returns the unique identifier for items in the collection
     * @param index of a user in the collection
     * @param item current user
     */
    trackIdentity(index: number, item: Organization) {
        return item.id;
    }
}
