import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { OrganizationSelectorComponent } from 'app/shared/organization-selector/organization-selector.component';
import { ArtemisTestModule } from '../../../test.module';
import { Observable } from 'rxjs';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { OrganizationManagementService } from 'app/admin/organization-management/organization-management.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { Organization } from 'app/entities/organization.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('OrganizationSelectorComponent', () => {
    let component: OrganizationSelectorComponent;
    let fixture: ComponentFixture<OrganizationSelectorComponent>;
    let organizationService: OrganizationManagementService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [OrganizationSelectorComponent],
            providers: [{ provide: LocalStorageService, useClass: MockSyncStorage }, { provide: SessionStorageService, useClass: MockSyncStorage }, MockProvider(TranslateService)],
        })
            .overrideTemplate(OrganizationSelectorComponent, '')
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(OrganizationSelectorComponent);
                component = fixture.componentInstance;
                organizationService = TestBed.inject(OrganizationManagementService);
            });
    });

    afterEach(async () => {
        jest.clearAllMocks();
    });

    it('should load organizations and filter out the already assigned ones', fakeAsync(() => {
        const organization1 = new Organization();
        organization1.id = 5;
        organization1.name = 'orgOne';
        const organization2 = new Organization();
        organization2.id = 6;
        organization2.name = 'orgTwo';

        component.organizations = [organization1];
        spyOn(organizationService, 'getOrganizations').and.returnValue(Observable.of([organization1, organization2]));

        fixture.detectChanges();
        expect(component).to.be.ok;
        expect(component.availableOrganizations[0]).to.be.eq(organization2);
    }));
});
