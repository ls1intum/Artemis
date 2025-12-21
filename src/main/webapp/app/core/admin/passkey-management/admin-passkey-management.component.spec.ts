import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AdminPasskeyManagementComponent } from './admin-passkey-management.component';
import { AdminPasskeyManagementService } from './admin-passkey-management.service';
import { AlertService } from 'app/shared/service/alert.service';
import { AdminPasskeyDTO } from './admin-passkey.dto';
import { MockProvider } from 'ng-mocks';
import { of, throwError } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('AdminPasskeyManagementComponent', () => {
    let component: AdminPasskeyManagementComponent;
    let fixture: ComponentFixture<AdminPasskeyManagementComponent>;
    let service: AdminPasskeyManagementService;
    let httpMock: HttpTestingController;

    const mockPasskeys: AdminPasskeyDTO[] = [
        {
            credentialId: 'cred1',
            label: 'My Passkey 1',
            created: '2023-01-01T00:00:00Z',
            lastUsed: '2023-01-02T00:00:00Z',
            isSuperAdminApproved: false,
            userId: 1,
            userLogin: 'user1',
            userName: 'User One',
        },
        {
            credentialId: 'cred2',
            label: 'My Passkey 2',
            created: '2023-02-01T00:00:00Z',
            lastUsed: '2023-02-02T00:00:00Z',
            isSuperAdminApproved: true,
            userId: 2,
            userLogin: 'user2',
            userName: 'User Two',
        },
    ];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [AdminPasskeyManagementComponent],
            providers: [
                AdminPasskeyManagementService,
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(AlertService),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        jest.spyOn(AdminPasskeyManagementService.prototype, 'getAllPasskeys').mockReturnValue(of([]));

        fixture = TestBed.createComponent(AdminPasskeyManagementComponent);
        component = fixture.componentInstance;
        service = TestBed.inject(AdminPasskeyManagementService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
        jest.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should load passkeys on init', () => {
        const getAllPasskeysSpy = jest.spyOn(service, 'getAllPasskeys').mockReturnValue(of(mockPasskeys));

        component.ngOnInit();

        expect(getAllPasskeysSpy).toHaveBeenCalledOnce();
        expect(component.passkeys()).toEqual(mockPasskeys);
        expect(component.isLoading()).toBeFalse();
    });

    it('should set loading state to true when loading passkeys', () => {
        jest.spyOn(service, 'getAllPasskeys').mockReturnValue(of(mockPasskeys));

        component.loadPasskeys();

        expect(component.passkeys()).toEqual(mockPasskeys);
        expect(component.isLoading()).toBeFalse();
    });

    it('should handle error when loading passkeys fails', () => {
        const errorResponse = new HttpErrorResponse({
            error: 'Error loading passkeys',
            status: 500,
            statusText: 'Internal Server Error',
        });
        jest.spyOn(service, 'getAllPasskeys').mockReturnValue(throwError(() => errorResponse));

        component.loadPasskeys();

        expect(component.isLoading()).toBeFalse();
    });

    it('should toggle approval status successfully', async () => {
        const passkey = { ...mockPasskeys[0] };
        const updatedPasskey = { ...passkey, isSuperAdminApproved: true };
        jest.spyOn(service, 'updatePasskeyApproval').mockReturnValue(of(updatedPasskey));

        component.passkeys.set([passkey]);

        await component.onApprovalToggle(passkey);

        expect(service.updatePasskeyApproval).toHaveBeenCalledWith('cred1', true);
        expect(passkey.isSuperAdminApproved).toBeTrue();
    });

    it('should toggle approval status from approved to not approved', async () => {
        const passkey = { ...mockPasskeys[1] };
        const updatedPasskey = { ...passkey, isSuperAdminApproved: false };
        jest.spyOn(service, 'updatePasskeyApproval').mockReturnValue(of(updatedPasskey));

        component.passkeys.set([passkey]);

        await component.onApprovalToggle(passkey);

        expect(service.updatePasskeyApproval).toHaveBeenCalledWith('cred2', false);
        expect(passkey.isSuperAdminApproved).toBeFalse();
    });

    it('should handle error when toggling approval status fails', () => {
        const passkey = { ...mockPasskeys[0] };
        const errorResponse = new HttpErrorResponse({
            error: 'Error updating approval',
            status: 500,
            statusText: 'Internal Server Error',
        });
        jest.spyOn(service, 'updatePasskeyApproval').mockReturnValue(throwError(() => errorResponse));

        component.passkeys.set([passkey]);
        const originalApprovalStatus = passkey.isSuperAdminApproved;

        component.onApprovalToggle(passkey);

        expect(passkey.isSuperAdminApproved).toBe(originalApprovalStatus);
    });

    it('should display empty state when no passkeys exist', () => {
        jest.spyOn(service, 'getAllPasskeys').mockReturnValue(of([]));

        component.ngOnInit();

        expect(component.passkeys()).toEqual([]);
        expect(component.passkeys()).toHaveLength(0);
    });
});
