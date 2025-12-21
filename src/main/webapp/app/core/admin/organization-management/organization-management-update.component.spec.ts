/**
 * Vitest tests for OrganizationManagementUpdateComponent.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { of } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { OrganizationManagementUpdateComponent } from 'app/core/admin/organization-management/organization-management-update.component';
import { OrganizationManagementService } from 'app/core/admin/organization-management/organization-management.service';
import { Organization } from 'app/core/shared/entities/organization.model';

describe('OrganizationManagementUpdateComponent', () => {
    setupTestBed({ zoneless: true });

    let component: OrganizationManagementUpdateComponent;
    let fixture: ComponentFixture<OrganizationManagementUpdateComponent>;
    let organizationService: OrganizationManagementService;

    const organization1 = new Organization();
    organization1.id = 5;

    const parentRoute = {
        data: of({ organization: organization1 }),
    } as any as ActivatedRoute;
    const route = { parent: parentRoute } as any as ActivatedRoute;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [OrganizationManagementUpdateComponent],
            providers: [{ provide: ActivatedRoute, useValue: route }, provideHttpClient(), provideHttpClientTesting()],
        })
            .overrideTemplate(OrganizationManagementUpdateComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(OrganizationManagementUpdateComponent);
        component = fixture.componentInstance;
        organizationService = TestBed.inject(OrganizationManagementService);
    });

    beforeEach(() => {
        vi.clearAllMocks();
    });

    describe('onInit', () => {
        it('should create', () => {
            expect(component).toBeTruthy();
        });

        it('should initialize and load organization from route if any', () => {
            organization1.name = 'orgOne';
            organization1.shortName = 'oO1';
            organization1.emailPattern = '.*1';

            vi.spyOn(organizationService, 'getOrganizationById').mockReturnValue(of(organization1));

            component.ngOnInit();

            expect(component.organization.id).toEqual(organization1.id);
        });
    });

    describe('Save', () => {
        it('should update the current edited organization', () => {
            organization1.name = 'updatedName';
            component.organization = organization1;
            vi.spyOn(organizationService, 'update').mockReturnValue(of(new HttpResponse<Organization>({ body: organization1 })));

            component.save();

            expect(organizationService.update).toHaveBeenCalledWith(organization1);
            expect(component.isSaving()).toBe(false);
        });

        it('should add the current created organization', () => {
            const newOrganization = new Organization();
            newOrganization.name = 'newOrg';
            newOrganization.shortName = 'newO';
            newOrganization.emailPattern = '.*';

            component.organization = newOrganization;
            vi.spyOn(organizationService, 'add').mockReturnValue(of(new HttpResponse<Organization>({ body: newOrganization })));

            component.save();

            expect(organizationService.add).toHaveBeenCalledWith(newOrganization);
            expect(component.isSaving()).toBe(false);
        });
    });
});
