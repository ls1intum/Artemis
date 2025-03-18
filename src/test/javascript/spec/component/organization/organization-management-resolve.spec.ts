import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot } from '@angular/router';
import { OrganizationManagementResolve } from 'app/core/admin/organization-management/organization-management-resolve.service';
import { OrganizationManagementService } from 'app/core/admin/organization-management/organization-management.service';
import { Organization } from 'app/entities/organization.model';
import { of } from 'rxjs';

describe('OrganizationManagementResolve', () => {
    let organizationManagementService: OrganizationManagementService;
    let organizationManagementResolve: OrganizationManagementResolve;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                OrganizationManagementResolve,
                {
                    provide: OrganizationManagementService,
                    useValue: {
                        getOrganizationById: jest.fn(),
                    },
                },
            ],
        });

        organizationManagementService = TestBed.inject(OrganizationManagementService);
        organizationManagementResolve = TestBed.inject(OrganizationManagementResolve);
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
