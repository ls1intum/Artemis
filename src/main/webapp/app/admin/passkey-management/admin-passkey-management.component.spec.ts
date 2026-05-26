import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AdminPasskeyManagementComponent } from './admin-passkey-management.component';
import { AdminPasskeyManagementService } from './admin-passkey-management.service';
import { AlertService } from 'app/shared/service/alert.service';
import { AdminPasskeyDTO } from './admin-passkey.dto';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('AdminPasskeyManagementComponent', () => {
    setupTestBed({ zoneless: true });

    let component: AdminPasskeyManagementComponent;
    let fixture: ComponentFixture<AdminPasskeyManagementComponent>;
    let adminPasskeyManagementService: AdminPasskeyManagementService;

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
                AlertService,
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        // default to an empty resolved promise
        vi.spyOn(AdminPasskeyManagementService.prototype, 'getAllPasskeys').mockReturnValue(Promise.resolve([]));

        fixture = TestBed.createComponent(AdminPasskeyManagementComponent);
        component = fixture.componentInstance;
        adminPasskeyManagementService = TestBed.inject(AdminPasskeyManagementService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should load passkeys on init', async () => {
        const getAllPasskeysSpy = vi.spyOn(adminPasskeyManagementService, 'getAllPasskeys').mockReturnValue(Promise.resolve(mockPasskeys));

        // let Angular run ngOnInit and resolve async Promises
        fixture.detectChanges();
        await fixture.whenStable();

        expect(getAllPasskeysSpy).toHaveBeenCalledOnce();
        expect(component.passkeys()).toEqual(mockPasskeys);
        expect(component.isLoading()).toBe(false);
    });

    it('should handle error when loading passkeys fails', async () => {
        const errorResponse = new HttpErrorResponse({
            error: 'Error loading passkeys',
            status: 500,
            statusText: 'Internal Server Error',
        });
        vi.spyOn(adminPasskeyManagementService, 'getAllPasskeys').mockReturnValue(Promise.reject(errorResponse));

        await component.loadPasskeys();

        expect(component.isLoading()).toBe(false);
    });

    it('should toggle approval status successfully', async () => {
        const passkey = { ...mockPasskeys[0] };
        const updatedPasskey = { ...passkey, isSuperAdminApproved: true };
        vi.spyOn(adminPasskeyManagementService, 'updatePasskeyApproval').mockReturnValue(Promise.resolve(updatedPasskey));

        component.passkeys.set([passkey]);

        await component.onApprovalToggle(passkey);

        expect(adminPasskeyManagementService.updatePasskeyApproval).toHaveBeenCalledWith('cred1', true);
        expect(passkey.isSuperAdminApproved).toBe(true);
    });

    it('should toggle approval status from approved to not approved', async () => {
        const passkey = { ...mockPasskeys[1] };
        const updatedPasskey = { ...passkey, isSuperAdminApproved: false };
        vi.spyOn(adminPasskeyManagementService, 'updatePasskeyApproval').mockReturnValue(Promise.resolve(updatedPasskey));

        component.passkeys.set([passkey]);

        await component.onApprovalToggle(passkey);

        expect(adminPasskeyManagementService.updatePasskeyApproval).toHaveBeenCalledWith('cred2', false);
        expect(passkey.isSuperAdminApproved).toBe(false);
    });

    it('should handle error when toggling approval status fails', async () => {
        const passkey = { ...mockPasskeys[0] };
        const errorResponse = new HttpErrorResponse({
            error: 'Error updating approval',
            status: 500,
            statusText: 'Internal Server Error',
        });
        vi.spyOn(adminPasskeyManagementService, 'updatePasskeyApproval').mockReturnValue(Promise.reject(errorResponse));

        component.passkeys.set([passkey]);
        const originalApprovalStatus = passkey.isSuperAdminApproved;

        await component.onApprovalToggle(passkey);

        expect(passkey.isSuperAdminApproved).toBe(originalApprovalStatus);
    });

    it('should display empty state when no passkeys exist', async () => {
        vi.spyOn(adminPasskeyManagementService, 'getAllPasskeys').mockReturnValue(Promise.resolve([]));

        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(component.passkeys()).toEqual([]);
        expect(component.passkeys()).toHaveLength(0);

        const emptyStateElement = fixture.nativeElement.querySelector('div.alert.alert-info[jhiTranslate="artemisApp.adminPasskeyManagement.noPasskeys"]');
        expect(emptyStateElement).toBeTruthy();
    });
});
