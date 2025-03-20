import { TestBed, fakeAsync } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { SshUserSettingsFingerprintsService } from 'app/shared/user-settings/ssh-settings/fingerprints/ssh-user-settings-fingerprints.service';

describe('SshUserSettingsFingerprintsService', () => {
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
        jest.restoreAllMocks();
    });

    it('should get SSH fingerprints', fakeAsync(() => {
        sshFingerprintsService.getSshFingerprints();
        httpMock.expectOne({ method: 'GET', url: getUserUrl });
    }));
});
