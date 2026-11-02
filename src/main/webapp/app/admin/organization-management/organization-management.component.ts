import { OrganizationListDirective } from './organization-list.directive';
import { ChangeDetectionStrategy, Component, TrackByFunction, computed, viewChild } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { Organization } from 'app/admin/organization-management/organization.model';
import { Subject } from 'rxjs';
import { faPencil, faPlus, faTrash } from '@fortawesome/free-solid-svg-icons';
import { CellTemplateRef, ColumnDef, TumAetUiButtonDirective, TumAetUiTableComponent, TumAetUiTableQueryEvent } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { AdminTitleBarTitleDirective } from 'app/admin/shared/admin-title-bar-title.directive';
import { AdminTitleBarActionsDirective } from 'app/admin/shared/admin-title-bar-actions.directive';

/**
 * Component for managing organizations.
 * Displays a list of organizations with user and course counts.
 */
@Component({
    selector: 'jhi-organization-management',
    templateUrl: './organization-management.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        TranslateDirective,
        RouterLink,
        FaIconComponent,
        DeleteButtonDirective,
        AdminTitleBarTitleDirective,
        AdminTitleBarActionsDirective,
        TumAetUiTableComponent,
        TumAetUiButtonDirective,
    ],
})
export class OrganizationManagementComponent extends OrganizationListDirective {
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

    // Row identity so the table reuses row DOM across reloads instead of rebuilding every row.
    protected readonly trackByOrgId: TrackByFunction<Organization> = (_, organization) => organization.id;

    // Icons
    faPlus = faPlus;
    faTrash = faTrash;
    faPencil = faPencil;

    private lastLoadEvent: TumAetUiTableQueryEvent | undefined;

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

    override loadOrganizations(event: TumAetUiTableQueryEvent): void {
        this.lastLoadEvent = event;
        super.loadOrganizations(event, true);
    }
}
