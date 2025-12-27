import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { OrganizationSelectorComponent } from 'app/shared/organization-selector/organization-selector.component';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { of } from 'rxjs';
import { OrganizationManagementService } from 'app/core/admin/organization-management/organization-management.service';
import { Organization } from 'app/core/shared/entities/organization.model';
import { MockProvider } from 'ng-mocks';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { provideHttpClient } from '@angular/common/http';

describe('OrganizationSelectorComponent', () => {
    let component: OrganizationSelectorComponent;
    let fixture: ComponentFixture<OrganizationSelectorComponent>;
    let organizationService: OrganizationManagementService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [LocalStorageService, SessionStorageService, MockProvider(NgbActiveModal), provideHttpClient()],
        })
            .overrideTemplate(OrganizationSelectorComponent, '')
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(OrganizationSelectorComponent);
                component = fixture.componentInstance;
                organizationService = TestBed.inject(OrganizationManagementService);
            });
    });

    beforeEach(() => {
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
        jest.spyOn(organizationService, 'getOrganizations').mockReturnValue(of([organization1, organization2]));

        fixture.changeDetectorRef.detectChanges();
        expect(component).not.toBeNull();
        expect(component.availableOrganizations[0]).toBe(organization2);
    }));
});
