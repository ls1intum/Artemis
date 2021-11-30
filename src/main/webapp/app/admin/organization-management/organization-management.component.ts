import { Component, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Organization } from 'app/entities/organization.model';
import { OrganizationManagementService } from 'app/admin/organization-management/organization-management.service';
import { Subject } from 'rxjs';
import { faEye, faPlus, faTimes, faWrench } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-organization-management',
    templateUrl: './organization-management.component.html',
})
export class OrganizationManagementComponent implements OnInit {
    organizations: Organization[];

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    // Icons
    faPlus = faPlus;
    faTimes = faTimes;
    faEye = faEye;
    faWrench = faWrench;

    constructor(private organizationService: OrganizationManagementService) {}

    ngOnInit(): void {
        this.organizationService.getOrganizations().subscribe((organizations) => {
            this.organizations = organizations;
            this.organizationService.getNumberOfUsersAndCoursesOfOrganizations().subscribe((organizationCountDtos) => {
                for (let i = 0; i < organizationCountDtos.length; i++) {
                    this.organizations[i].numberOfUsers = organizationCountDtos[i].numberOfUsers;
                    this.organizations[i].numberOfCourses = organizationCountDtos[i].numberOfCourses;
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
