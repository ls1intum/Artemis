import { Component, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Organization } from 'app/entities/organization.model';
import { UserService } from 'app/core/user/user.service';
import { OrganizationManagementService } from 'app/admin/organization-management/organization-management.service';
import { JhiAlertService } from 'ng-jhipster';
import { Subject } from 'rxjs';

@Component({
    selector: 'jhi-organization-management',
    templateUrl: './organization-management.component.html',
})
export class OrganizationManagementComponent implements OnInit {
    organizations: Organization[];

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    constructor(private organizationService: OrganizationManagementService, private userService: UserService, private alertService: JhiAlertService) {}

    ngOnInit(): void {
        this.organizationService.getOrganizations().subscribe((organizations) => {
            this.organizations = organizations;
            this.organizationService.getNumberOfUsersAndCoursesOfOrganizations().subscribe((data) => {
                for (let i = 0; i < data.length; i++) {
                    this.organizations[i].numberOfUsers = data[i].numberOfUsers;
                    this.organizations[i].numberOfCourses = data[i].numberOfCourses;
                }
            });
        });
    }

    deleteOrganization(organizationId: number) {
        this.organizationService.deleteOrganization(organizationId).subscribe(
            () => {
                this.dialogErrorSource.next('');
                this.organizations = this.organizations.filter((org) => org.id !== organizationId);
            },
            (error: HttpErrorResponse) => {
                this.dialogErrorSource.next('An error occurred while removing the organization: ' + error.message);
            },
        );
    }

    /**
     * Returns the unique identifier for items in the collection
     * @param index of a user in the collection
     * @param item current user
     */
    trackIdentity(index: number, item: Organization) {
        return item.id ?? -1;
    }
}
