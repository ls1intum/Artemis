import { ActivatedRouteSnapshot } from '@angular/router';
import { OrganizationManagementResolve } from 'app/admin/organization-management/organization-management-resolve.service';
import { OrganizationManagementService } from 'app/admin/organization-management/organization-management.service';
import { Organization } from 'app/entities/organization.model';
import { of } from 'rxjs';

describe('OrganizationManagementResolve', () => {
    let organizationManagementService: OrganizationManagementService;
    let organizationManagementResolve: OrganizationManagementResolve;

    beforeEach(() => {
        organizationManagementService = { getOrganizationById: jest.fn() } as any as OrganizationManagementService;
        organizationManagementResolve = new OrganizationManagementResolve(organizationManagementService);
    });

    it('should search for organization by id', () => {
        const toReturn = new Organization();
        const spy = jest.spyOn(organizationManagementService, 'getOrganizationById').mockReturnValue(of(toReturn));

        let result = undefined;
        // @ts-ignore
        organizationManagementResolve.resolve({ params: { id: 1 } } as any as ActivatedRouteSnapshot).subscribe((org) => (result = org));
        expect(result).toBe(toReturn);
        expect(spy).toHaveBeenCalledOnce();
        expect(spy).toHaveBeenCalledWith(1);
    });

    it('should return new organization if no id is given', () => {
        const toReturn = new Organization();
        const spy = jest.spyOn(organizationManagementService, 'getOrganizationById').mockReturnValue(of(toReturn));

        const result = organizationManagementResolve.resolve({ params: { id: undefined } } as any as ActivatedRouteSnapshot);
        expect(result).not.toBe(toReturn);
        expect(result).toBeInstanceOf(Organization);
        expect(spy).not.toHaveBeenCalled();
    });
});
