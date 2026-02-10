import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Organization } from 'app/core/shared/entities/organization.model';
import { OrganizationManagementService } from 'app/core/admin/organization-management/organization-management.service';
import { Subject } from 'rxjs';
import { faEye, faPenToSquare, faPlus, faTimes, faTrashCan, faWrench } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
// import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { AdminTitleBarTitleDirective } from 'app/core/admin/shared/admin-title-bar-title.directive';
import { AdminTitleBarActionsDirective } from 'app/core/admin/shared/admin-title-bar-actions.directive';
import { TableView } from 'app/shared/table-view/table-view';
import { buildDbQueryFromLazyEvent } from 'app/shared/table-view/request-builder';
import { ButtonModule } from 'primeng/button';

export type OrganizationKey = keyof Organization;

/**
 * Component for managing organizations.
 * Displays a list of organizations with user and course counts.
 */
@Component({
    selector: 'jhi-organization-management',
    templateUrl: './organization-management.component.html',
    styleUrl: './organization-management.component.scss',
    imports: [TranslateDirective, RouterLink, FaIconComponent, AdminTitleBarTitleDirective, AdminTitleBarActionsDirective, TableView, ButtonModule],
})
export class OrganizationManagementComponent implements OnInit {
    private readonly organizationService = inject(OrganizationManagementService);

    /** List of organizations */
    organizations = signal<any[]>([]);
    columns = signal<any[]>([]);
    totalRows = signal(0);

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    // Icons
    faPlus = faPlus;
    faTimes = faTimes;
    faEye = faEye;
    faWrench = faWrench;
    faPenToSquare = faPenToSquare;
    faTrashCan = faTrashCan;

    /**
     * Loads organizations and their user/course counts on initialization.
     */
    ngOnInit(): void {
        this.organizationService.getOrganizations({ page: 0, pageSize: 2, sort: 'id,asc' }).subscribe((organizations) => {
            this.organizations.set(organizations.data);
            this.totalRows.set(organizations.total);
        });
        this.columns.set([
            { field: 'id' satisfies OrganizationKey, header: 'ID', sort: true, filter: false, filterType: 'text' },
            { field: 'name' satisfies OrganizationKey, header: 'Name', sort: true, filter: false, filterType: 'text' },
            { field: 'shortName' satisfies OrganizationKey, header: 'Short Name', sort: true, filter: false, filterType: 'text' },
            { field: 'url' satisfies OrganizationKey, header: 'URL', sort: true, filter: false, filterType: 'text' },
            { field: 'description' satisfies OrganizationKey, header: 'Description', sort: true, filter: false, filterType: 'text' },
            { field: 'numberOfUsers' satisfies OrganizationKey, header: 'Users', sort: false, filter: false, filterType: 'text' },
            { field: 'numberOfCourses' satisfies OrganizationKey, header: 'Courses', sort: false, filter: false, filterType: 'text' },
            { field: 'emailPattern' satisfies OrganizationKey, header: 'Email Pattern', sort: true, filter: false, filterType: 'text' },
        ]);
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

    loadOrganizations(event: any): void {
        const q = buildDbQueryFromLazyEvent(event);
        this.organizationService.getOrganizations({ page: q.page, pageSize: q.size, sort: q.sort }).subscribe((organizations) => {
            this.organizations.set(organizations.data);
            this.totalRows.set(organizations.total);
        });
    }
}
