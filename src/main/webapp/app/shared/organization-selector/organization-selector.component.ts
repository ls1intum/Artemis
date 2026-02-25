import { Component, TemplateRef, computed, inject, signal, viewChild } from '@angular/core';
import { OrganizationManagementService } from 'app/core/admin/organization-management/organization-management.service';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Organization } from 'app/core/shared/entities/organization.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { RouterLink } from '@angular/router';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { TableLazyLoadEvent } from 'primeng/table';
import { CellRendererParams, ColumnDef, TableView } from 'app/shared/table-view/table-view';
import { buildDbQueryFromLazyEvent } from 'app/shared/table-view/request-builder';

export interface OrganizationSelectorDialogData {
    organizations?: Organization[];
}

@Component({
    selector: 'jhi-organization-selector',
    templateUrl: './organization-selector.component.html',
    imports: [TranslateDirective, RouterLink, HasAnyAuthorityDirective, TableView],
})
export class OrganizationSelectorComponent {
    private readonly dialogRef = inject(DynamicDialogRef);
    private readonly config = inject(DynamicDialogConfig<OrganizationSelectorDialogData>);
    private readonly organizationService = inject(OrganizationManagementService);

    readonly organizations = signal<Organization[]>([]);
    readonly totalCount = signal(0);
    readonly isLoading = signal(false);

    private readonly assignedOrgIds = new Set<number>((this.config.data?.organizations ?? []).map((o: any) => o.id).filter((id: any): id is number => id !== undefined));

    private readonly logoTemplate = viewChild<TemplateRef<{ $implicit: CellRendererParams<Organization> }>>('logoCell');

    readonly columns = computed<ColumnDef<Organization>[]>(() => [
        { field: 'logoUrl', width: '64px', templateRef: this.logoTemplate() },
        { field: 'name', headerKey: 'artemisApp.organizationManagement.name', sort: true },
        { field: 'shortName', headerKey: 'artemisApp.organizationManagement.shortName', sort: true },
        { field: 'emailPattern', headerKey: 'artemisApp.organizationManagement.emailPattern', sort: true },
    ]);

    isAlreadyAssigned = computed(() => (org: Organization) => this.assignedOrgIds.has(org.id!));

    loadOrganizations(event: TableLazyLoadEvent): void {
        this.isLoading.set(true);
        const query = buildDbQueryFromLazyEvent(event);
        this.organizationService.getOrganizations(query).subscribe({
            next: (response) => {
                this.organizations.set(response.content);
                this.totalCount.set(response.totalElements);
                this.isLoading.set(false);
            },
            error: () => {
                this.organizations.set([]);
                this.totalCount.set(0);
                this.isLoading.set(false);
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
