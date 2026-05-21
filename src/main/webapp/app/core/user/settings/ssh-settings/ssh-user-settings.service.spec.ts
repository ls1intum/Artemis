import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { UserSshPublicKey } from 'app/programming/shared/entities/user-ssh-public-key.model';
import dayjs from 'dayjs/esm';
import { SshUserSettingsService } from 'app/core/user/settings/ssh-settings/ssh-user-settings.service';

describe('SshUserSettingsService', () => {
    setupTestBed({ zoneless: true });

    let sshUserSettingsService: SshUserSettingsService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        sshUserSettingsService = TestBed.inject(SshUserSettingsService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
        // Reset the service state
        sshUserSettingsService.sshKeys = undefined;
    });

    it('should handle user SSH public key correctly', () => {
        const sshKey = new UserSshPublicKey();
        sshKey.id = 123;
        sshKey.label = 'test-label';

        expect(sshKey.id).toBe(123);
        expect(sshKey.label).toBe('test-label');
    });

    describe('sshKeys getter and setter', () => {
        it('should get and set sshKeys correctly', () => {
            const testKeys: UserSshPublicKey[] = [
                { id: 1, label: 'key1', publicKey: 'ssh-rsa AAAA1', keyHash: 'hash1', creationDate: dayjs() },
                { id: 2, label: 'key2', publicKey: 'ssh-rsa AAAA2', keyHash: 'hash2', creationDate: dayjs() },
            ];

            sshUserSettingsService.sshKeys = testKeys;

            expect(sshUserSettingsService.sshKeys).toEqual(testKeys);
        });

        it('should return undefined when sshKeys not set', () => {
            expect(sshUserSettingsService.sshKeys).toBeUndefined();
        });
    });

    describe('getCachedSshKeys', () => {
        it('should return cached keys immediately if already available', async () => {
            const testKeys: UserSshPublicKey[] = [{ id: 1, label: 'key1', publicKey: 'ssh-rsa AAAA1', keyHash: 'hash1', creationDate: dayjs() }];
            sshUserSettingsService.sshKeys = testKeys;

            const result = await sshUserSettingsService.getCachedSshKeys();

            expect(result).toEqual(testKeys);
        });

        it('should fetch keys from server if not cached', async () => {
            const testKeys: UserSshPublicKey[] = [{ id: 1, label: 'key1', publicKey: 'ssh-rsa AAAA1', keyHash: 'hash1', creationDate: dayjs() }];

            const promise = sshUserSettingsService.getCachedSshKeys();

            const req = httpMock.expectOne({ method: 'GET', url: 'api/programming/ssh-settings/public-keys' });
            req.flush(testKeys);

            const result = await promise;
            expect(result).toEqual(testKeys);
        });

        it('should return the same promise if a request is already in progress', async () => {
            const testKeys: UserSshPublicKey[] = [{ id: 1, label: 'key1', publicKey: 'ssh-rsa AAAA1', keyHash: 'hash1', creationDate: dayjs() }];

            const promise1 = sshUserSettingsService.getCachedSshKeys();
            const promise2 = sshUserSettingsService.getCachedSshKeys();

            expect(promise1).toBe(promise2);

            const req = httpMock.expectOne({ method: 'GET', url: 'api/programming/ssh-settings/public-keys' });
            req.flush(testKeys);

            const result1 = await promise1;
            const result2 = await promise2;
            expect(result1).toEqual(result2);
        });

        it('should handle error and return undefined', async () => {
            const promise = sshUserSettingsService.getCachedSshKeys();

            const req = httpMock.expectOne({ method: 'GET', url: 'api/programming/ssh-settings/public-keys' });
            req.error(new ProgressEvent('error'));

            const result = await promise;
            expect(result).toBeUndefined();
            expect(sshUserSettingsService.sshKeys).toBeUndefined();
        });

        it('should handle empty keys response', async () => {
            const promise = sshUserSettingsService.getCachedSshKeys();

            const req = httpMock.expectOne({ method: 'GET', url: 'api/programming/ssh-settings/public-keys' });
            req.flush([]);

            const result = await promise;
            expect(result).toEqual([]);
        });

        it('should handle null keys response', async () => {
            const promise = sshUserSettingsService.getCachedSshKeys();

            const req = httpMock.expectOne({ method: 'GET', url: 'api/programming/ssh-settings/public-keys' });
            req.flush(null);

            const result = await promise;
            // When keys is falsy, it returns the existing sshKeys (undefined)
            expect(result).toBeUndefined();
        });
    });

    describe('test SSH user settings API calls', () => {
        let userSshPublicKey: UserSshPublicKey;
        beforeEach(() => {
            userSshPublicKey = new UserSshPublicKey();
            userSshPublicKey.publicKey = 'ssh-key 1234';
            userSshPublicKey.label = 'key 1';
            userSshPublicKey.id = 1;
            userSshPublicKey.expiryDate = dayjs().subtract(5, 'day');
        });

        afterEach(() => {
            httpMock.verify();
        });

        it('should send a new SSH public key', () => {
            sshUserSettingsService.addNewSshPublicKey(userSshPublicKey).subscribe((response) => {
                expect(response.body).toEqual(userSshPublicKey);
            });

            const req = httpMock.expectOne({ method: 'POST', url: 'api/programming/ssh-settings/public-key' });
            req.flush(userSshPublicKey);

            expect(req.request.method).toBe('POST');
        });

        it('should retrieve all SSH public keys', () => {
            sshUserSettingsService.getSshPublicKeys().subscribe(() => {});

            const req = httpMock.expectOne({ method: 'GET', url: 'api/programming/ssh-settings/public-keys' });
            req.flush({});
            expect(req.request.method).toBe('GET');
        });

        it('should retrieve a specific SSH public key', () => {
            const keyId = 1;
            sshUserSettingsService.getSshPublicKey(keyId).subscribe(() => {});

            const req = httpMock.expectOne({ method: 'GET', url: `api/programming/ssh-settings/public-key/${keyId}` });
            req.flush({});
            expect(req.request.method).toBe('GET');
        });

        it('should delete a specific SSH public key', () => {
            const keyId = 1;

            sshUserSettingsService.deleteSshPublicKey(keyId).subscribe(() => {});

            const req = httpMock.expectOne({ method: 'DELETE', url: `api/programming/ssh-settings/public-key/${keyId}` });
            req.flush(null);
        });
    });
});
