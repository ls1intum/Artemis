import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Organization } from 'app/core/shared/entities/organization.model';
import { OrganizationManagementService } from 'app/core/admin/organization-management/organization-management.service';
import { Subject } from 'rxjs';
import { faEye, faPlus, faTimes, faWrench } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';

/**
 * Component for managing organizations.
 * Displays a list of organizations with user and course counts.
 */
@Component({
    selector: 'jhi-organization-management',
    templateUrl: './organization-management.component.html',
    imports: [TranslateDirective, RouterLink, FaIconComponent, DeleteButtonDirective],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrganizationManagementComponent implements OnInit {
    private readonly organizationService = inject(OrganizationManagementService);

    /** List of organizations */
    readonly organizations = signal<Organization[]>([]);

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    // Icons
    faPlus = faPlus;
    faTimes = faTimes;
    faEye = faEye;
    faWrench = faWrench;

    /**
     * Loads organizations and their user/course counts on initialization.
     */
    ngOnInit(): void {
        this.organizationService.getOrganizations().subscribe((organizations) => {
            this.organizations.set(organizations);
            this.organizationService.getNumberOfUsersAndCoursesOfOrganizations().subscribe((organizationCountDtos) => {
                const orgs = this.organizations();
                for (let i = 0; i < organizationCountDtos.length; i++) {
                    orgs[i].numberOfUsers = organizationCountDtos[i].numberOfUsers;
                    orgs[i].numberOfCourses = organizationCountDtos[i].numberOfCourses;
                }
                this.organizations.set([...orgs]);
            });
        });
    }

    /**
     * Deletes an organization by ID.
     * @param organizationId - The ID of the organization to delete
     */
    deleteOrganization(organizationId: number): void {
        this.organizationService.deleteOrganization(organizationId).subscribe({
            next: () => {
                this.dialogErrorSource.next('');
                this.organizations.set(this.organizations().filter((org) => org.id !== organizationId));
            },
            error: (error: HttpErrorResponse) => {
                this.dialogErrorSource.next('An error occurred while removing the organization: ' + error.message);
            },
        });
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
