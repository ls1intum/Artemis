import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { AdminPasskeyManagementService } from './admin-passkey-management.service';
import { AdminPasskeyDTO } from './admin-passkey.dto';
import { PasskeyDTO } from 'app/core/user/settings/passkey-settings/dto/passkey.dto';

describe('AdminPasskeyManagementService', () => {
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

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [AdminPasskeyManagementService, provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(AdminPasskeyManagementService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    describe('getAllPasskeys', () => {
        it('should retrieve all passkeys with user information', async () => {
            const promise = service.getAllPasskeys();

            const req = httpMock.expectOne('api/core/passkey/admin');
            expect(req.request.method).toBe('GET');
            req.flush(mockPasskeys);

            const passkeys = await promise;
            expect(passkeys).toEqual(mockPasskeys);
            expect(passkeys).toHaveLength(2);
            expect(passkeys[0].userLogin).toBe('user1');
            expect(passkeys[1].userLogin).toBe('user2');
        });

        it('should handle empty passkey list', async () => {
            const promise = service.getAllPasskeys();

            const req = httpMock.expectOne('api/core/passkey/admin');
            expect(req.request.method).toBe('GET');
            req.flush([]);

            const passkeys = await promise;
            expect(passkeys).toEqual([]);
            expect(passkeys).toHaveLength(0);
        });

        it('should handle error when retrieving passkeys fails', async () => {
            const errorMessage = 'Failed to load passkeys';
            const promise = service.getAllPasskeys();

            const req = httpMock.expectOne('api/core/passkey/admin');
            expect(req.request.method).toBe('GET');
            req.flush(errorMessage, { status: 500, statusText: 'Internal Server Error' });

            try {
                await promise;
                throw new Error('should have failed with 500 error');
            } catch (error: any) {
                expect(error.status).toBe(500);
                expect(error.error).toBe(errorMessage);
            }
        });
    });

    describe('updatePasskeyApproval', () => {
        it('should update passkey approval status to approved', async () => {
            const credentialId = 'cred1';
            const isSuperAdminApproved = true;
            const mockResponse: PasskeyDTO = {
                credentialId,
                label: 'My Passkey',
                created: new Date('2023-01-01').toISOString(),
                lastUsed: new Date('2023-01-02').toISOString(),
                isSuperAdminApproved,
            };

            const promise = service.updatePasskeyApproval(credentialId, isSuperAdminApproved);

            const req = httpMock.expectOne(`api/core/passkey/${credentialId}/approval`);
            expect(req.request.method).toBe('PUT');
            expect(req.request.body).toBeTrue();
            req.flush(mockResponse);

            const response = await promise;
            expect(response).toEqual(mockResponse);
            expect(response.isSuperAdminApproved).toBeTrue();
        });

        it('should update passkey approval status to not approved', async () => {
            const credentialId = 'cred2';
            const isSuperAdminApproved = false;
            const mockResponse: PasskeyDTO = {
                credentialId,
                label: 'My Passkey 2',
                created: new Date('2023-02-01').toISOString(),
                lastUsed: new Date('2023-02-02').toISOString(),
                isSuperAdminApproved,
            };

            const promise = service.updatePasskeyApproval(credentialId, isSuperAdminApproved);

            const req = httpMock.expectOne(`api/core/passkey/${credentialId}/approval`);
            expect(req.request.method).toBe('PUT');
            expect(req.request.body).toBeFalse();
            req.flush(mockResponse);

            const response = await promise;
            expect(response).toEqual(mockResponse);
            expect(response.isSuperAdminApproved).toBeFalse();
        });

        it('should handle error when updating approval status fails', async () => {
            const credentialId = 'cred1';
            const errorMessage = 'Failed to update approval status';
            const promise = service.updatePasskeyApproval(credentialId, true);

            const req = httpMock.expectOne(`api/core/passkey/${credentialId}/approval`);
            expect(req.request.method).toBe('PUT');
            req.flush(errorMessage, { status: 500, statusText: 'Internal Server Error' });

            try {
                await promise;
                throw new Error('should have failed with 500 error');
            } catch (error: any) {
                expect(error.status).toBe(500);
                expect(error.error).toBe(errorMessage);
            }
        });

        it('should handle 404 error when passkey not found', async () => {
            const credentialId = 'nonexistent';
            const errorMessage = 'Passkey not found';
            const promise = service.updatePasskeyApproval(credentialId, true);

            const req = httpMock.expectOne(`api/core/passkey/${credentialId}/approval`);
            expect(req.request.method).toBe('PUT');
            req.flush(errorMessage, { status: 404, statusText: 'Not Found' });

            try {
                await promise;
                throw new Error('should have failed with 404 error');
            } catch (error: any) {
                expect(error.status).toBe(404);
                expect(error.error).toBe(errorMessage);
            }
        });
    });
});
