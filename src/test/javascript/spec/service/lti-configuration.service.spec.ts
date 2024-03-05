import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { LtiPlatformConfiguration } from 'app/admin/lti-configuration/lti-configuration.model';
import { LtiConfigurationService } from 'app/admin/lti-configuration/lti-configuration.service';

describe('LtiConfigurationService', () => {
    let service: LtiConfigurationService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [LtiConfigurationService],
        });
        service = TestBed.inject(LtiConfigurationService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should return all lti platforms', () => {
        const dummyLtiPlatforms: LtiPlatformConfiguration[] = [
            {
                id: 1,
                customName: 'Platform A',
                clientId: 'client-id-a',
                authorizationUri: 'http://lms.com/api/lti_consumer/v1/auth-login',
                jwkSetUri: 'http://lms.com/api/lti_consumer/v1/public_keysets',
                tokenUri: 'http://lms.com/api/lti_consumer/v1/token/4d4faae2-65e4-400f-91d7-9cc22d0489a9',
            },
            {
                id: 2,
                customName: 'Platform B',
                clientId: 'client-id-b',
                authorizationUri: 'http://lms.com/api/lti_consumer/v1/auth-login',
                jwkSetUri: 'http://lms.com/api/lti_consumer/v1/public_keysets',
                tokenUri: 'http://lms.com/api/lti_consumer/v1/token/4d4faae2-65e4-400f-91d7-9cc22d0489a9',
            },
            {
                id: 1,
                customName: 'Platform A',
                clientId: 'client-id-a',
                authorizationUri: 'platformA.com/auth-login',
                jwkSetUri: 'platformA.com/jwk',
                tokenUri: 'platformA.com/token',
            },
            {
                id: 2,
                customName: 'Platform B',
                clientId: 'client-id-b',
                authorizationUri: 'platformB.com/auth-login',
                jwkSetUri: 'platformB.com/jwk',
                tokenUri: 'platformB.com/token',
            },
        ];

        service.findAll().subscribe((platforms) => {
            expect(platforms.length).toHaveLength(2);
            expect(platforms).toEqual(dummyLtiPlatforms);
        });

        const req = httpMock.expectOne('api/lti-platforms');
        expect(req.request.method).toBe('GET');
        req.flush(dummyLtiPlatforms);
    });

    it('should update lti platform', () => {
        const dummyResponse = { status: 200, statusText: 'OK' };
        const dummyConfig: LtiPlatformConfiguration = {
            id: 1,
            customName: 'Updated Platform',
            clientId: 'updated-client-id',
            authorizationUri: 'platformA.com/auth-login',
            jwkSetUri: 'platformA.com/jwk',
            tokenUri: 'platformA.com/token',
        };

        service.updateLtiPlatformConfiguration(dummyConfig).subscribe((response) => {
            expect(response.status).toBe(200);
        });

        const req = httpMock.expectOne(`api/admin/lti-platform`);
        expect(req.request.method).toBe('PUT');
        req.flush(dummyResponse);
    });

    it('should send a delete a lti response', () => {
        const platformId = 123;
        const dummyResponse = { status: 200, statusText: 'Deleted' };

        service.deleteLtiPlatform(platformId).subscribe((response) => {
            expect(response.status).toBe(200);
        });

        const req = httpMock.expectOne(`api/admin/lti-platform/${platformId}`);
        expect(req.request.method).toBe('DELETE');
        req.flush(dummyResponse);
    });

    it('should add new lti platform', () => {
        const dummyResponse = { status: 200, statusText: 'OK' };
        const dummyConfig: LtiPlatformConfiguration = {
            customName: 'New Platform',
            clientId: 'created-client-id',
            authorizationUri: 'http://lms.com/api/lti_consumer/v1/auth-login',
            jwkSetUri: 'http://lms.com/api/lti_consumer/v1/public_keysets',
            tokenUri: 'http://lms.com/api/lti_consumer/v1/token/4d4faae2-65e4-400f-91d7-9cc22d0489a9',
        };

        service.addLtiPlatformConfiguration(dummyConfig).subscribe((response) => {
            expect(response.status).toBe(200);
        });

        const req = httpMock.expectOne(`api/admin/lti-platform`);
        expect(req.request.method).toBe('POST');
        req.flush(dummyResponse);
    });
});
