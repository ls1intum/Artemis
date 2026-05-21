import { Component, computed, inject, signal, viewChild } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Organization } from 'app/core/shared/entities/organization.model';
import { OrganizationManagementService } from 'app/core/admin/organization-management/organization-management.service';
import { Subject } from 'rxjs';
import { faPlus, faTimes, faWrench } from '@fortawesome/free-solid-svg-icons';
import { TableLazyLoadEvent } from 'primeng/table';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { AdminTitleBarTitleDirective } from 'app/core/admin/shared/admin-title-bar-title.directive';
import { AdminTitleBarActionsDirective } from 'app/core/admin/shared/admin-title-bar-actions.directive';
import { CellTemplateRef, ColumnDef, TableViewComponent, TableViewOptions } from 'app/shared/table-view/table-view';
import { buildDbQueryFromLazyEvent } from 'app/shared/table-view/request-builder';
import { AlertService } from 'app/shared/service/alert.service';
import { onError } from 'app/shared/util/global.utils';

/**
 * Component for managing organizations.
 * Displays a list of organizations with user and course counts.
 */
@Component({
    selector: 'jhi-organization-management',
    templateUrl: './organization-management.component.html',
    imports: [TranslateDirective, RouterLink, FaIconComponent, DeleteButtonDirective, AdminTitleBarTitleDirective, AdminTitleBarActionsDirective, TableViewComponent],
})
export class OrganizationManagementComponent {
    private readonly organizationService = inject(OrganizationManagementService);
    private readonly router = inject(Router);
    private readonly route = inject(ActivatedRoute);
    private readonly alertService = inject(AlertService);

    readonly tableOptions: TableViewOptions = { striped: true, scrollable: true, scrollHeight: 'flex' };

    organizations = signal<Organization[]>([]);
    totalCount = signal(0);
    isLoading = signal(false);

    nameColumnTemplate = viewChild<CellTemplateRef<Organization>>('nameColumn');

    columns = computed<ColumnDef<Organization>[]>(() => [
        { field: 'name', headerKey: 'artemisApp.organizationManagement.name', sort: true, width: '300px', templateRef: this.nameColumnTemplate() },
        { field: 'shortName', headerKey: 'artemisApp.organizationManagement.shortName', sort: true, width: '150px' },
        { field: 'numberOfUsers', headerKey: 'artemisApp.organizationManagement.users', sort: true, width: '100px' },
        { field: 'numberOfCourses', headerKey: 'artemisApp.organizationManagement.courses', sort: true, width: '100px' },
        { field: 'emailPattern', headerKey: 'artemisApp.organizationManagement.emailPattern', sort: true, width: '200px' },
    ]);
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    // Icons
    faPlus = faPlus;
    faTimes = faTimes;
    faWrench = faWrench;

    private lastLoadEvent: TableLazyLoadEvent | undefined;
    private loadRequestId = 0;

    /**
     * Deletes an organization by ID and refreshes the current page.
     * @param organizationId - The ID of the organization to delete
     */
    deleteOrganization(organizationId: number | undefined): void {
        if (organizationId === undefined) {
            return;
        }
        this.organizationService.deleteOrganization(organizationId).subscribe({
            next: () => {
                this.dialogErrorSource.next('');
                if (this.lastLoadEvent) {
                    this.loadOrganizations(this.lastLoadEvent);
                }
            },
            error: (error: HttpErrorResponse) => {
                this.dialogErrorSource.next(error.message);
            },
        });
    }

    loadOrganizations(event: TableLazyLoadEvent): void {
        this.lastLoadEvent = event;
        this.isLoading.set(true);
        const requestId = ++this.loadRequestId;
        const query = buildDbQueryFromLazyEvent(event);
        this.organizationService.getOrganizations(query, true).subscribe({
            next: (response) => {
                if (requestId !== this.loadRequestId) return;
                this.organizations.set(response.content);
                this.totalCount.set(response.totalElements);
                this.isLoading.set(false);
            },
            error: (error: HttpErrorResponse) => {
                if (requestId !== this.loadRequestId) return;
                this.organizations.set([]);
                this.totalCount.set(0);
                this.isLoading.set(false);
                onError(this.alertService, error);
            },
        });
    }

    onOrganizationSelect(organization: Organization | Organization[] | undefined): void {
        if (!Array.isArray(organization) && organization?.id != null) {
            this.router.navigate([organization.id], { relativeTo: this.route });
        }
    }
}
