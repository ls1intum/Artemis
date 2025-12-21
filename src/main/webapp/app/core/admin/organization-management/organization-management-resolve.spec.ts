/**
 * Vitest tests for OrganizationManagementResolve.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ActivatedRouteSnapshot } from '@angular/router';
import { of } from 'rxjs';

import { OrganizationManagementResolve } from 'app/core/admin/organization-management/organization-management-resolve.service';
import { OrganizationManagementService } from 'app/core/admin/organization-management/organization-management.service';
import { Organization } from 'app/core/shared/entities/organization.model';

describe('OrganizationManagementResolve', () => {
    setupTestBed({ zoneless: true });

    let organizationManagementService: OrganizationManagementService;
    let organizationManagementResolve: OrganizationManagementResolve;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                OrganizationManagementResolve,
                {
                    provide: OrganizationManagementService,
                    useValue: {
                        getOrganizationById: vi.fn(),
                    },
                },
            ],
        });

        organizationManagementService = TestBed.inject(OrganizationManagementService);
        organizationManagementResolve = TestBed.inject(OrganizationManagementResolve);
    });

    it('should search for organization by id', () => {
        const toReturn = new Organization();
        const spy = vi.spyOn(organizationManagementService, 'getOrganizationById').mockReturnValue(of(toReturn));

        let result = undefined;
        // @ts-ignore
        organizationManagementResolve.resolve({ params: { id: 1 } } as any as ActivatedRouteSnapshot).subscribe((org) => (result = org));
        expect(result).toBe(toReturn);
        expect(spy).toHaveBeenCalledOnce();
        expect(spy).toHaveBeenCalledWith(1);
    });

    it('should return new organization if no id is given', () => {
        const toReturn = new Organization();
        const spy = vi.spyOn(organizationManagementService, 'getOrganizationById').mockReturnValue(of(toReturn));

        const result = organizationManagementResolve.resolve({ params: { id: undefined } } as any as ActivatedRouteSnapshot);
        expect(result).not.toBe(toReturn);
        expect(result).toBeInstanceOf(Organization);
        expect(spy).not.toHaveBeenCalled();
    });
});
