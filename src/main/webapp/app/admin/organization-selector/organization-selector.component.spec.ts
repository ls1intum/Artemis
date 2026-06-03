import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { OrganizationSelectorComponent } from 'app/admin/organization-selector/organization-selector.component';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { of, throwError } from 'rxjs';
import { OrganizationManagementService } from 'app/admin/organization-management/organization-management.service';
import { Organization } from 'app/admin/organization-management/organization.model';
import { MockProvider } from 'ng-mocks';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { provideHttpClient } from '@angular/common/http';
import { TableLazyLoadEvent } from 'primeng/table';
import { AlertService } from 'app/foundation/service/alert.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';

describe('OrganizationSelectorComponent', () => {
    setupTestBed({ zoneless: true });
    let component: OrganizationSelectorComponent;
    let fixture: ComponentFixture<OrganizationSelectorComponent>;
    let organizationService: OrganizationManagementService;

    const organization1 = new Organization();
    organization1.id = 5;
    organization1.name = 'orgOne';

    const organization2 = new Organization();
    organization2.id = 6;
    organization2.name = 'orgTwo';

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [
                LocalStorageService,
                SessionStorageService,
                MockProvider(DynamicDialogRef),
                MockProvider(AlertService),
                {
                    provide: DynamicDialogConfig,
                    useValue: {
                        data: {
                            organizations: [organization1],
                        },
                    },
                },
                provideHttpClient(),
                { provide: AccountService, useClass: MockAccountService },
            ],
        })
            .overrideTemplate(OrganizationSelectorComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(OrganizationSelectorComponent);
        component = fixture.componentInstance;
        organizationService = TestBed.inject(OrganizationManagementService);
        vi.clearAllMocks();
    });

    it('should load organizations via paginated endpoint', () => {
        vi.spyOn(organizationService, 'getOrganizations').mockReturnValue(of({ content: [organization1, organization2], totalElements: 2 }));

        component.loadOrganizations({} as TableLazyLoadEvent);

        expect(component).toBeTruthy();
        expect(component.isLoading()).toBe(false);
        expect(component.totalCount()).toBe(2);
        expect(component.organizations()).toHaveLength(2);
    });

    it('should call getOrganizations without withCounts (uses default false)', () => {
        const spy = vi.spyOn(organizationService, 'getOrganizations').mockReturnValue(of({ content: [], totalElements: 0 }));

        component.loadOrganizations({} as TableLazyLoadEvent);

        // No second argument means the service default (false) is used — no counts requested
        expect(spy).toHaveBeenCalledOnce();
        expect(spy.mock.calls[0]).toHaveLength(1);
    });

    it('should mark already-assigned organizations as disabled', () => {
        // organization1 (id=5) is in the dialog config as already assigned
        expect(component.isAlreadyAssigned()(organization1)).toBe(true);
        expect(component.isAlreadyAssigned()(organization2)).toBe(false);
    });

    it('should handle error when loading organizations', () => {
        vi.spyOn(organizationService, 'getOrganizations').mockReturnValue(throwError(() => new Error('Network error')));

        component.loadOrganizations({} as TableLazyLoadEvent);

        expect(component.isLoading()).toBe(false);
        expect(component.totalCount()).toBe(0);
        expect(component.organizations()).toHaveLength(0);
    });

    it('should close modal with organization', () => {
        const dialogRef = TestBed.inject(DynamicDialogRef);
        const closeSpy = vi.spyOn(dialogRef, 'close');

        component.selectOrganization(organization1);

        expect(closeSpy).toHaveBeenCalledWith(organization1);
    });

    it('should close modal with undefined on cancel', () => {
        const dialogRef = TestBed.inject(DynamicDialogRef);
        const closeSpy = vi.spyOn(dialogRef, 'close');

        component.closeModal(undefined);

        expect(closeSpy).toHaveBeenCalledWith(undefined);
    });
});
