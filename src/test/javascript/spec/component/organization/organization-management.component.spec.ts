import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { OrganizationCountDto } from 'app/admin/organization-management/organization-count-dto.model';

import { OrganizationManagementComponent } from 'app/admin/organization-management/organization-management.component';
import { OrganizationManagementService } from 'app/admin/organization-management/organization-management.service';
import { Organization } from 'app/entities/organization.model';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of } from 'rxjs';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../test.module';

describe('OrganizationManagementComponent', () => {
    let component: OrganizationManagementComponent;
    let fixture: ComponentFixture<OrganizationManagementComponent>;
    let organizationService: OrganizationManagementService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [OrganizationManagementComponent],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
            ],
        })
            .overrideTemplate(OrganizationManagementComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(OrganizationManagementComponent);
        component = fixture.componentInstance;
        organizationService = TestBed.inject(OrganizationManagementService);
    });

    beforeEach(() => {
        jest.clearAllMocks();
    });

    it('should initialize and load organizations', fakeAsync(() => {
        const organization1 = new Organization();
        organization1.id = 5;
        organization1.name = 'orgOne';
        const organization2 = new Organization();
        organization2.id = 6;
        organization2.name = 'orgTwo';

        const countOrg1 = new OrganizationCountDto();
        countOrg1.organizationId = organization1.id;
        countOrg1.numberOfUsers = 1;
        countOrg1.numberOfCourses = 1;

        const countOrg2 = new OrganizationCountDto();
        countOrg2.organizationId = organization2.id;
        countOrg2.numberOfUsers = 2;
        countOrg2.numberOfCourses = 2;

        const numOfUsersAndCoursesOfOrganizations = [countOrg1, countOrg2];

        jest.spyOn(organizationService, 'getOrganizations').mockReturnValue(of([organization1, organization2]));
        jest.spyOn(organizationService, 'getNumberOfUsersAndCoursesOfOrganizations').mockReturnValue(of(numOfUsersAndCoursesOfOrganizations));

        fixture.detectChanges();
        expect(component).not.toBeNull();
        expect(component.organizations).toHaveLength(2);
        expect(component.organizations[0].numberOfUsers).toBe(1);
        expect(component.organizations[0].numberOfCourses).toBe(1);
        expect(component.organizations[1].numberOfUsers).toBe(2);
        expect(component.organizations[1].numberOfCourses).toBe(2);
    }));

    it('should delete an organization', fakeAsync(() => {
        const organization1 = new Organization();
        organization1.id = 5;
        organization1.name = 'orgOne';

        component.organizations = [organization1];
        jest.spyOn(organizationService, 'deleteOrganization').mockReturnValue(of(new HttpResponse<void>()));

        component.deleteOrganization(5);
        fixture.detectChanges();
        tick();
        expect(component).not.toBeNull();
        expect(component.organizations).toHaveLength(0);
    }));

    it('should track id', fakeAsync(() => {
        const organization1 = new Organization();
        organization1.id = 5;
        organization1.name = 'orgOne';

        expect(component.trackIdentity(0, organization1)).toBe(5);
    }));
});
