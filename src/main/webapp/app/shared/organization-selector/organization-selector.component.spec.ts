import { ComponentFixture, TestBed } from '@angular/core/testing';
import { OrganizationSelectorComponent } from 'app/shared/organization-selector/organization-selector.component';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { of, throwError } from 'rxjs';
import { OrganizationManagementService } from 'app/core/admin/organization-management/organization-management.service';
import { Organization } from 'app/core/shared/entities/organization.model';
import { MockProvider } from 'ng-mocks';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { provideHttpClient } from '@angular/common/http';
import { TableLazyLoadEvent } from 'primeng/table';

describe('OrganizationSelectorComponent', () => {
    let component: OrganizationSelectorComponent;
    let fixture: ComponentFixture<OrganizationSelectorComponent>;
    let organizationService: OrganizationManagementService;

    const organization1 = new Organization();
    organization1.id = 5;
    organization1.name = 'orgOne';

    const organization2 = new Organization();
    organization2.id = 6;
    organization2.name = 'orgTwo';

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                LocalStorageService,
                SessionStorageService,
                MockProvider(DynamicDialogRef),
                {
                    provide: DynamicDialogConfig,
                    useValue: {
                        data: {
                            organizations: [organization1],
                        },
                    },
                },
                provideHttpClient(),
            ],
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

    it('should load organizations via paginated endpoint', () => {
        jest.spyOn(organizationService, 'getOrganizations').mockReturnValue(of({ content: [organization1, organization2], totalElements: 2 }));

        component.loadOrganizations({} as TableLazyLoadEvent);

        expect(component).toBeTruthy();
        expect(component.isLoading()).toBeFalse();
        expect(component.totalCount()).toBe(2);
        expect(component.organizations()).toHaveLength(2);
    });

    it('should call getOrganizations without withCounts (uses default false)', () => {
        const spy = jest.spyOn(organizationService, 'getOrganizations').mockReturnValue(of({ content: [], totalElements: 0 }));

        component.loadOrganizations({} as TableLazyLoadEvent);

        // No second argument means the service default (false) is used — no counts requested
        expect(spy).toHaveBeenCalledWith(expect.anything());
    });

    it('should mark already-assigned organizations as disabled', () => {
        // organization1 (id=5) is in the dialog config as already assigned
        expect(component.isAlreadyAssigned()(organization1)).toBeTrue();
        expect(component.isAlreadyAssigned()(organization2)).toBeFalse();
    });

    it('should handle error when loading organizations', () => {
        jest.spyOn(organizationService, 'getOrganizations').mockReturnValue(throwError(() => new Error('Network error')));

        component.loadOrganizations({} as TableLazyLoadEvent);

        expect(component.isLoading()).toBeFalse();
        expect(component.totalCount()).toBe(0);
        expect(component.organizations()).toHaveLength(0);
    });

    it('should close modal with organization', () => {
        const dialogRef = TestBed.inject(DynamicDialogRef);
        const closeSpy = jest.spyOn(dialogRef, 'close');

        component.selectOrganization(organization1);

        expect(closeSpy).toHaveBeenCalledWith(organization1);
    });

    it('should close modal with undefined on cancel', () => {
        const dialogRef = TestBed.inject(DynamicDialogRef);
        const closeSpy = jest.spyOn(dialogRef, 'close');

        component.closeModal(undefined);

        expect(closeSpy).toHaveBeenCalledWith(undefined);
    });
});
