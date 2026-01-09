import { Component, OnInit, inject, signal } from '@angular/core';
import { OrganizationManagementService } from 'app/core/admin/organization-management/organization-management.service';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Organization } from 'app/core/shared/entities/organization.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';

export interface OrganizationSelectorDialogData {
    organizations?: Organization[];
}

@Component({
    selector: 'jhi-organization-selector',
    templateUrl: './organization-selector.component.html',
    imports: [TranslateDirective],
})
export class OrganizationSelectorComponent implements OnInit {
    private readonly dialogRef = inject(DynamicDialogRef);
    private readonly config = inject(DynamicDialogConfig<OrganizationSelectorDialogData>);
    private readonly organizationService = inject(OrganizationManagementService);

    readonly organizations = signal<Organization[] | undefined>(this.config.data?.organizations);
    availableOrganizations: Organization[];

    ngOnInit(): void {
        this.organizationService.getOrganizations().subscribe((data) => {
            this.availableOrganizations = data;
            const currentOrganizations = this.organizations();
            if (currentOrganizations !== undefined) {
                this.availableOrganizations = this.availableOrganizations.filter((organization) => {
                    return !currentOrganizations.some((currentOrganization) => currentOrganization.id === organization.id);
                });
            }
        });
    }

    closeModal(organization: Organization | undefined) {
        this.dialogRef.close(organization);
    }
}
