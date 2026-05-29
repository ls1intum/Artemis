import { Component, computed, inject, signal, viewChild } from '@angular/core';
import { OrganizationManagementService } from 'app/admin/organization-management/organization-management.service';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Organization } from 'app/admin/organization-management/organization.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TableLazyLoadEvent } from 'primeng/table';
import { CellTemplateRef, ColumnDef, TableViewComponent, TableViewOptions } from 'app/shared-ui/table-view/table-view';
import { buildDbQueryFromLazyEvent } from 'app/shared-ui/table-view/request-builder';
import { AlertService } from 'app/foundation/service/alert.service';
import { onError } from 'app/foundation/util/global.utils';
import { HttpErrorResponse } from '@angular/common/http';

export interface OrganizationSelectorDialogData {
    organizations?: Organization[];
}

@Component({
    selector: 'jhi-organization-selector',
    templateUrl: './organization-selector.component.html',
    imports: [TranslateDirective, TableViewComponent],
})
export class OrganizationSelectorComponent {
    readonly selectorTableOptions: TableViewOptions = {
        pageSize: 10,
        hidePageSizeOptions: true,
        emptyMessageTranslation: 'artemisApp.organizationManagement.modalSelector.noOrganizations',
        striped: true,
    };

    private readonly dialogRef = inject(DynamicDialogRef);
    private readonly config = inject(DynamicDialogConfig<OrganizationSelectorDialogData>);
    private readonly organizationService = inject(OrganizationManagementService);
    private readonly alertService = inject(AlertService);

    readonly organizations = signal<Organization[]>([]);
    readonly totalCount = signal(0);
    readonly isLoading = signal(false);

    private loadRequestId = 0;

    private readonly logoTemplate = viewChild<CellTemplateRef<Organization>>('logoCell');

    protected readonly assignedOrgIds = new Set<number>(
        (this.config.data?.organizations ?? []).map((o: Organization) => o.id).filter((id: number | undefined): id is number => id !== undefined),
    );

    readonly columns = computed<ColumnDef<Organization>[]>(() => [
        { field: 'logoUrl', templateRef: this.logoTemplate() },
        { field: 'name', headerKey: 'artemisApp.organizationManagement.name', sort: true },
        { field: 'shortName', headerKey: 'artemisApp.organizationManagement.shortName', sort: true },
        { field: 'emailPattern', headerKey: 'artemisApp.organizationManagement.emailPattern', sort: true },
    ]);

    isAlreadyAssigned = computed(() => (org: Organization) => org.id !== undefined && this.assignedOrgIds.has(org.id));

    loadOrganizations(event: TableLazyLoadEvent): void {
        this.isLoading.set(true);
        const requestId = ++this.loadRequestId;
        const query = buildDbQueryFromLazyEvent(event);
        this.organizationService.getOrganizations(query).subscribe({
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

    selectOrganization(organization: Organization): void {
        this.dialogRef.close(organization);
    }

    closeModal(organization: Organization | undefined): void {
        this.dialogRef.close(organization);
    }
}
