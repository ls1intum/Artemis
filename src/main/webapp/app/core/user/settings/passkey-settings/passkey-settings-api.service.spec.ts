import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { PasskeySettingsApiService } from './passkey-settings-api.service';
import { PasskeyDTO } from './dto/passkey.dto';

// Mock data
const mockPasskeys: PasskeyDTO[] = [
    {
        credentialId: 'credential-id-1',
        label: 'Passkey 1',
        created: '2024-01-01T12:00:00Z',
        lastUsed: '2024-01-02T12:00:00Z',
        isSuperAdminApproved: true,
    },
    {
        credentialId: 'credential-id-2',
        label: 'Passkey 2',
        created: '2024-02-01T12:00:00Z',
        lastUsed: '2024-02-02T12:00:00Z',
        isSuperAdminApproved: false,
    },
];

describe('PasskeySettingsApiService', () => {
    setupTestBed({ zoneless: true });

    let service: PasskeySettingsApiService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [PasskeySettingsApiService, provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(PasskeySettingsApiService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    describe('getRegisteredPasskeys', () => {
        it('should get registered passkeys for the current user', async () => {
            const promise = service.getRegisteredPasskeys();
            const req = httpMock.expectOne('api/core/passkey/user');
            expect(req.request.method).toBe('GET');
            req.flush(mockPasskeys);
            const result = await promise;
            expect(result).toEqual(mockPasskeys);
        });

        it('should return an empty array when no passkeys are registered', async () => {
            const promise = service.getRegisteredPasskeys();
            const req = httpMock.expectOne('api/core/passkey/user');
            expect(req.request.method).toBe('GET');
            req.flush([]);
            const result = await promise;
            expect(result).toEqual([]);
        });
    });

    describe('updatePasskeyLabel', () => {
        it('should update a passkey label', async () => {
            const credentialId = 'credential-id-1';
            const updatedPasskey: PasskeyDTO = {
                ...mockPasskeys[0],
                label: 'Updated Passkey Label',
            };
            const promise = service.updatePasskeyLabel(credentialId, updatedPasskey);
            const req = httpMock.expectOne(`api/core/passkey/${credentialId}`);
            expect(req.request.method).toBe('PUT');
            expect(req.request.body).toEqual(updatedPasskey);
            req.flush(updatedPasskey);
            const result = await promise;
            expect(result).toEqual(updatedPasskey);
        });

        it('should handle updating passkey with special characters in credential ID', async () => {
            const credentialId = 'credential-id-with-special-chars_123';
            const updatedPasskey: PasskeyDTO = {
                credentialId: credentialId,
                label: 'Special Passkey',
                created: '2024-03-01T12:00:00Z',
                lastUsed: '2024-03-02T12:00:00Z',
                isSuperAdminApproved: false,
            };
            const promise = service.updatePasskeyLabel(credentialId, updatedPasskey);
            const req = httpMock.expectOne(`api/core/passkey/${credentialId}`);
            expect(req.request.method).toBe('PUT');
            req.flush(updatedPasskey);
            const result = await promise;
            expect(result).toEqual(updatedPasskey);
        });
    });

    describe('deletePasskey', () => {
        it('should delete a passkey', async () => {
            const credentialId = 'credential-id-1';
            const promise = service.deletePasskey(credentialId);
            const req = httpMock.expectOne(`api/core/passkey/${credentialId}`);
            expect(req.request.method).toBe('DELETE');
            req.flush(null);
            const result = await promise;
            expect(result).toBeNull();
        });

        it('should handle deleting passkey with special characters in credential ID', async () => {
            const credentialId = 'credential-id-with-special-chars_456';
            const promise = service.deletePasskey(credentialId);
            const req = httpMock.expectOne(`api/core/passkey/${credentialId}`);
            expect(req.request.method).toBe('DELETE');
            req.flush(null);
            const result = await promise;
            expect(result).toBeNull();
        });
    });
});
