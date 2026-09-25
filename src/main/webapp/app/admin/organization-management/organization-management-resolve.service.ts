import { Organization } from 'app/admin/organization-management/organization.model';
import { OrganizationManagementService } from 'app/admin/organization-management/organization-management.service';
import { Service, inject } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve } from '@angular/router';

@Service()
export class OrganizationManagementResolve implements Resolve<Organization> {
    private organizationManagementService = inject(OrganizationManagementService);

    resolve(route: ActivatedRouteSnapshot) {
        if (route.params['id']) {
            return this.organizationManagementService.getOrganizationById(route.params['id']);
        }
        return new Organization();
    }
}
