import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { Observable, of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';

import { OrganizationManagementComponent } from 'app/admin/organization-management/organization-management.component';
import { OrganizationManagementService } from 'app/admin/organization-management/organization-management.service';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute } from '@angular/router';
import { Organization } from 'app/entities/organization.model';

chai.use(sinonChai);
const expect = chai.expect;

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

    afterEach(async () => {
        jest.clearAllMocks();
    });

    it('should initialize and load organizations', fakeAsync(() => {
        const organization1 = new Organization();
        organization1.id = 5;
        organization1.name = 'orgOne';
        const organization2 = new Organization();
        organization2.id = 6;
        organization2.name = 'orgTwo';

        const numOfUsersAndCoursesOfOrganizations = [];
        numOfUsersAndCoursesOfOrganizations.push({ organizationId: 5, numberOfUsers: 1, numberOfCourses: 1 });
        numOfUsersAndCoursesOfOrganizations.push({ organizationId: 6, numberOfUsers: 2, numberOfCourses: 2 });

        component.organizations = [organization1];
        spyOn(organizationService, 'getOrganizations').and.returnValue(Observable.of([organization1, organization2]));
        spyOn(organizationService, 'getNumberOfUsersAndCoursesOfOrganizations').and.returnValue(Observable.of(numOfUsersAndCoursesOfOrganizations));

        fixture.detectChanges();
        expect(component).to.be.ok;
        expect(component.organizations.length).to.be.eq(2);
        expect(component.organizations[0].numberOfUsers).to.be.eq(1);
        expect(component.organizations[0].numberOfCourses).to.be.eq(1);
        expect(component.organizations[1].numberOfUsers).to.be.eq(2);
        expect(component.organizations[1].numberOfCourses).to.be.eq(2);
    }));

    it('should delete an organization', fakeAsync(() => {
        const organization1 = new Organization();
        organization1.id = 5;
        organization1.name = 'orgOne';

        component.organizations = [organization1];
        spyOn(organizationService, 'deleteOrganization').and.returnValue(of(new HttpResponse<void>()));

        component.deleteOrganization(5);
        fixture.detectChanges();
        tick();
        expect(component).to.be.ok;
        expect(component.organizations.length).to.be.equal(0);
    }));

    it('should track id', fakeAsync(() => {
        const organization1 = new Organization();
        organization1.id = 5;
        organization1.name = 'orgOne';

        expect(component.trackIdentity(0, organization1)).to.equal(5);
    }));
});
