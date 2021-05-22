import { TestBed } from '@angular/core/testing';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { SERVER_API_URL } from 'app/app.constants';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { MockRouter } from '../helpers/mocks/service/mock-route.service';
import { Router } from '@angular/router';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';

describe('Logs Service', () => {
    let service: ProfileService;
    let httpMock: HttpTestingController;

    const profileInfo: ProfileInfo = {
        activeProfiles: [],
        allowedMinimumOrionVersion: '',
        buildPlanURLTemplate: '',
        commitHashURLTemplate: '',
        contact: '',
        externalUserManagementName: '',
        externalUserManagementURL: '',
        features: [],
        inProduction: false,
        programmingLanguageFeatures: [],
        ribbonEnv: '',
        sshCloneURLTemplate: 'ssh://git@bitbucket.ase.in.tum.de:7999/',
        sshKeysURL: 'sshKeysURL',
        testServer: false,
        versionControlUrl: 'https://bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git',
        openApiEnabled: false,
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: Router, useClass: MockRouter },
            ],
        });
        service = TestBed.inject(ProfileService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('Service methods', () => {
        it('should call correct URL', () => {
            service.getProfileInfo().subscribe(() => {});

            const req = httpMock.expectOne({ method: 'GET' });
            const infoUrl = SERVER_API_URL + 'management/info';
            expect(req.request.url).toEqual(infoUrl);
        });

        it('should get the profile info', () => {
            const expected = profileInfo;

            let requestResult = {} as ProfileInfo;

            service.getProfileInfo().subscribe((received) => {
                if (received) {
                    requestResult = received;
                }
            });

            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(profileInfo);
            expect(requestResult).toEqual(expected);
        });
    });
});
