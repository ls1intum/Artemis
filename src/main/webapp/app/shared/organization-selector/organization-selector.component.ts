import { Component, OnInit } from '@angular/core';
import { OrganizationManagementService } from 'app/admin/organization-management/organization-management.service';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/core/util/alert.service';
import { Organization } from 'app/entities/organization.model';

@Component({
    selector: 'jhi-organization-selector',
    templateUrl: './organization-selector.component.html',
})
export class OrganizationSelectorComponent implements OnInit {
    organizations: Organization[];
    availableOrganizations: Organization[];

    constructor(
        private activeModal: NgbActiveModal,
        private translateService: TranslateService,
        private alertService: AlertService,
        private organizationService: OrganizationManagementService,
    ) {}

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
