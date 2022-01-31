import { Organization } from 'app/entities/organization.model';
import { OrganizationManagementService } from 'app/admin/organization-management/organization-management.service';
import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve } from '@angular/router';

@Injectable({ providedIn: 'root' })
export class OrganizationManagementResolve implements Resolve<Organization> {
    constructor(private organizationManagementService: OrganizationManagementService) {}

    resolve(route: ActivatedRouteSnapshot) {
        if (route.params['id']) {
            return this.organizationManagementService.getOrganizationById(route.params['id']);
        }
        return new Organization();
    }
}
