/**
 * Vitest tests for OrganizationManagementComponent.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { of } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
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
            providers: [LocalStorageService, SessionStorageService, { provide: ActivatedRoute, useValue: { data: of({}) } }, provideHttpClient(), provideHttpClientTesting()],
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

    it('should initialize and load organizations', () => {
        const organization1 = new Organization();
        organization1.id = 5;
        organization1.name = 'orgOne';
        const organization2 = new Organization();
        organization2.id = 6;
        organization2.name = 'orgTwo';

        vi.spyOn(organizationService, 'getOrganizations').mockReturnValue(of([organization1, organization2]));

        fixture.detectChanges();
        expect(component).not.toBeNull();
        expect(component.organizations()).toHaveLength(2);
        expect(component.organizations()[0].numberOfUsers).toBe(1);
        expect(component.organizations()[0].numberOfCourses).toBe(1);
        expect(component.organizations()[1].numberOfUsers).toBe(2);
        expect(component.organizations()[1].numberOfCourses).toBe(2);
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

    it('should track id', () => {
        const organization1 = new Organization();
        organization1.id = 5;
        organization1.name = 'orgOne';

        expect(component.trackIdentity(0, organization1)).toBe(5);
    });
});
