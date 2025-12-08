import { Component, OnInit, inject } from '@angular/core';
import { OrganizationManagementService } from 'app/core/admin/organization-management/organization-management.service';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Organization } from 'app/core/shared/entities/organization.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-organization-selector',
    templateUrl: './organization-selector.component.html',
    imports: [TranslateDirective],
})
export class OrganizationSelectorComponent implements OnInit {
    private activeModal = inject(NgbActiveModal);
    private organizationService = inject(OrganizationManagementService);

    organizations: Organization[];
    availableOrganizations: Organization[];

    ngOnInit(): void {
        this.organizationService.getOrganizations().subscribe((data) => {
            this.availableOrganizations = data;
            if (this.organizations !== undefined) {
                this.availableOrganizations = this.availableOrganizations.filter((organization) => {
                    return !this.organizations.some((currentOrganization) => currentOrganization.id === organization.id);
                });
            }
        });
    }

    closeModal(organization: Organization | undefined) {
        this.activeModal.close(organization);
    }
}
