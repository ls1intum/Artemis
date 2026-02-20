import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { Organization } from 'app/core/shared/entities/organization.model';
import { OrganizationManagementService } from 'app/core/admin/organization-management/organization-management.service';
import { Subject } from 'rxjs';
import { faEye, faPlus, faTimes, faWrench } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { AdminTitleBarTitleDirective } from 'app/core/admin/shared/admin-title-bar-title.directive';
import { AdminTitleBarActionsDirective } from 'app/core/admin/shared/admin-title-bar-actions.directive';
import { ColumnDef, TableView } from 'app/shared/table-view/table-view';
import { buildDbQueryFromLazyEvent } from 'app/shared/table-view/request-builder';

export type OrganizationKey = keyof Organization;

/**
 * Component for managing organizations.
 * Displays a list of organizations with user and course counts.
 */
@Component({
    selector: 'jhi-organization-management',
    templateUrl: './organization-management.component.html',
    styleUrl: './organization-management.component.scss',
    imports: [TranslateDirective, RouterLink, FaIconComponent, DeleteButtonDirective, AdminTitleBarTitleDirective, AdminTitleBarActionsDirective, TableView],
})
export class OrganizationManagementComponent {
    private readonly organizationService = inject(OrganizationManagementService);
    private readonly router = inject(Router);
    private readonly route = inject(ActivatedRoute);

    organizations = signal<Organization[]>([]);
    totalCount = signal(0);
    isLoading = signal(false);
    columns: ColumnDef<Organization>[] = [
        { field: 'id' satisfies OrganizationKey, headerKey: 'global.field.id', sort: true, width: '100px' },
        { field: 'name' satisfies OrganizationKey, headerKey: 'artemisApp.organizationManagement.name', sort: true, width: '300px' },
        { field: 'shortName' satisfies OrganizationKey, headerKey: 'artemisApp.organizationManagement.shortName', sort: true, width: '150px' },
        { field: 'numberOfUsers' satisfies OrganizationKey, headerKey: 'artemisApp.organizationManagement.users', sort: true, width: '100px' },
        { field: 'numberOfCourses' satisfies OrganizationKey, headerKey: 'artemisApp.organizationManagement.courses', sort: true, width: '100px' },
        { field: 'emailPattern' satisfies OrganizationKey, headerKey: 'artemisApp.organizationManagement.emailPattern', sort: true, width: '200px' },
    ];
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    // Icons
    faPlus = faPlus;
    faTimes = faTimes;
    faEye = faEye;
    faWrench = faWrench;

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

    loadOrganizations(event: any): void {
        this.isLoading.set(true);
        const query = buildDbQueryFromLazyEvent(event);
        this.organizationService.getOrganizations(query).subscribe((response) => {
            this.organizations.set(response.data);
            this.totalCount.set(response.total);
            this.isLoading.set(false);
        });
    }

    onOrganizationSelect(organization: Organization): void {
        this.router.navigate([organization.id], { relativeTo: this.route });
    }
}
