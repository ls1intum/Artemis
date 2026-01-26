import { afterEach, beforeEach, describe, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { SshUserSettingsFingerprintsService } from 'app/core/user/settings/ssh-settings/fingerprints/ssh-user-settings-fingerprints.service';

describe('SshUserSettingsFingerprintsService', () => {
    setupTestBed({ zoneless: true });

    let sshFingerprintsService: SshUserSettingsFingerprintsService;
    let httpMock: HttpTestingController;

    const getUserUrl = 'api/programming/ssh-fingerprints';

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        sshFingerprintsService = TestBed.inject(SshUserSettingsFingerprintsService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    it('should get SSH fingerprints', async () => {
        const promise = sshFingerprintsService.getSshFingerprints();
        const req = httpMock.expectOne({ method: 'GET', url: getUserUrl });
        req.flush({});
        await promise;
    });
});
