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
            { id: 1, customName: 'Platform A', clientId: 'client-id-a' },
            { id: 2, customName: 'Platform B', clientId: 'client-id-b' },
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
        const dummyConfig: LtiPlatformConfiguration = { id: 1, customName: 'Updated Platform', clientId: 'updated-client-id' };

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
});
