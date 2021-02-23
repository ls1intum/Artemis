import { Component, OnInit } from '@angular/core';
import { OrganizationManagementService } from 'app/admin/organization-management/organization-management.service';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { JhiAlertService } from 'ng-jhipster';
import { Organization } from 'app/entities/organization.model';
import { Course } from 'app/entities/course.model';

@Component({
    selector: 'jhi-organization-selector',
    templateUrl: './organization-selector.component.html',
})
export class OrganizationSelectorComponent implements OnInit {
    organizations: Organization[];
    availableOrganizations: Organization[];
    course: Course;

    constructor(
        private activeModal: NgbActiveModal,
        private translateService: TranslateService,
        private jhiAlertService: JhiAlertService,
        private organizationService: OrganizationManagementService,
    ) {}

    ngOnInit(): void {
        this.organizationService.getOrganizations().subscribe((data) => {
            this.availableOrganizations = data;
            if (this.organizations !== undefined) {
                this.availableOrganizations.forEach((organization) => {
                    this.organizations.forEach((currentOrganization) => {
                        if (organization.id === currentOrganization.id) {
                            this.availableOrganizations = this.availableOrganizations.filter((org) => org.id !== organization.id);
                        }
                    });
                });
            }
        });
    }

    closeModal(organization: Organization | undefined) {
        this.activeModal.close(organization);
    }
}
