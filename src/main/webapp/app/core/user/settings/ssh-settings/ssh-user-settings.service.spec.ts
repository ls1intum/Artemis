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
    });

    it('should handle user SSH public key correctly', () => {
        const sshKey = new UserSshPublicKey();
        sshKey.id = 123;
        sshKey.label = 'test-label';

        expect(sshKey.id).toBe(123);
        expect(sshKey.label).toBe('test-label');
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
            req.flush({});

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
