/**
 * Vitest tests for OrganizationManagementComponent.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { of, throwError } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { TableLazyLoadEvent } from 'primeng/table';
import { MockProvider } from 'ng-mocks';

import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { AlertService } from 'app/shared/service/alert.service';
import { OrganizationManagementComponent } from 'app/core/admin/organization-management/organization-management.component';
import { OrganizationManagementService } from 'app/core/admin/organization-management/organization-management.service';
import { Organization } from 'app/core/shared/entities/organization.model';

describe('OrganizationManagementComponent', () => {
    setupTestBed({ zoneless: true });

    let component: OrganizationManagementComponent;
    let fixture: ComponentFixture<OrganizationManagementComponent>;
    let organizationService: OrganizationManagementService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [OrganizationManagementComponent],
            providers: [
                LocalStorageService,
                SessionStorageService,
                { provide: ActivatedRoute, useValue: { data: of({}) } },
                { provide: Router, useValue: { navigate: vi.fn() } },
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(AlertService),
            ],
        })
            .overrideTemplate(OrganizationManagementComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(OrganizationManagementComponent);
        component = fixture.componentInstance;
        organizationService = TestBed.inject(OrganizationManagementService);
    });

    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('should load organizations', () => {
        const organization1 = new Organization();
        organization1.id = 5;
        organization1.name = 'orgOne';
        organization1.numberOfUsers = 1;
        organization1.numberOfCourses = 1;
        const organization2 = new Organization();
        organization2.id = 6;
        organization2.name = 'orgTwo';
        organization2.numberOfUsers = 2;
        organization2.numberOfCourses = 2;

        vi.spyOn(organizationService, 'getOrganizations').mockReturnValue(of({ content: [organization1, organization2], totalElements: 2 }));

        component.loadOrganizations({} as TableLazyLoadEvent);

        expect(organizationService.getOrganizations).toHaveBeenCalledWith(expect.anything(), true);
        expect(component).not.toBeNull();
        expect(component.isLoading()).toBe(false);
        expect(component.totalCount()).toBe(2);
        expect(component.organizations()).toHaveLength(2);
        expect(component.organizations()[0].numberOfUsers).toBe(1);
        expect(component.organizations()[0].numberOfCourses).toBe(1);
        expect(component.organizations()[1].numberOfUsers).toBe(2);
        expect(component.organizations()[1].numberOfCourses).toBe(2);
    });

    it('should handle error when loading organizations', () => {
        vi.spyOn(organizationService, 'getOrganizations').mockReturnValue(throwError(() => new Error('Network error')));

        component.loadOrganizations({} as TableLazyLoadEvent);
        expect(component.isLoading()).toBe(false);
        expect(component.totalCount()).toBe(0);
        expect(component.organizations()).toHaveLength(0);
    });

    it('should delete an organization', () => {
        const organization1 = new Organization();
        organization1.id = 5;
        organization1.name = 'orgOne';

        component.organizations.set([organization1]);
        vi.spyOn(organizationService, 'deleteOrganization').mockReturnValue(of(new HttpResponse<void>()));

        component.deleteOrganization(5);
        fixture.changeDetectorRef.detectChanges();
        expect(component).not.toBeNull();
        expect(component.organizations()).toHaveLength(0);
    });

    it('should navigate to organization details on select', () => {
        const organization = new Organization();
        organization.id = 5;
        organization.name = 'orgOne';

        const router = TestBed.inject(Router);
        const navigateSpy = vi.spyOn(router, 'navigate');

        component.onOrganizationSelect(organization);
        expect(navigateSpy).toHaveBeenCalledWith([5], { relativeTo: component['route'] });
    });
});
