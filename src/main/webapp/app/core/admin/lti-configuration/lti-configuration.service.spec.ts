/**
 * Vitest tests for LtiConfigurationService.
 */
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';

import { LtiPlatformConfiguration } from 'app/lti/shared/entities/lti-configuration.model';
import { LtiConfigurationService } from 'app/core/admin/lti-configuration/lti-configuration.service';
import { ITEMS_PER_PAGE } from 'app/shared/constants/pagination.constants';

describe('LtiConfigurationService', () => {
    setupTestBed({ zoneless: true });

    let service: LtiConfigurationService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), LtiConfigurationService],
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

        service
            .query({
                page: 0,
                size: ITEMS_PER_PAGE,
                sort: ['id', 'desc'],
            })
            .subscribe((response) => {
                expect(response.body).toHaveLength(4);
                expect(response.body).toEqual(dummyLtiPlatforms);
            });

        const req = httpMock.expectOne('api/lti/lti-platforms?page=0&size=50&sort=id&sort=desc');
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

        const req = httpMock.expectOne(`api/lti/admin/lti-platform`);
        expect(req.request.method).toBe('PUT');
        req.flush(dummyResponse);
    });

    it('should send a delete a lti response', () => {
        const platformId = 123;
        const dummyResponse = { status: 200, statusText: 'Deleted' };

        service.deleteLtiPlatform(platformId).subscribe((response) => {
            expect(response.status).toBe(200);
        });

        const req = httpMock.expectOne(`api/lti/admin/lti-platform/${platformId}`);
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

        const req = httpMock.expectOne(`api/lti/admin/lti-platform`);
        expect(req.request.method).toBe('POST');
        req.flush(dummyResponse);
    });

    it('should query with different sorting parameters', () => {
        service.query({ sort: ['name,asc', 'id,desc'] }).subscribe();
        const req = httpMock.expectOne('api/lti/lti-platforms?sort=name,asc&sort=id,desc');
        expect(req.request.method).toBe('GET');
        req.flush([]);
    });

    it('should handle errors when querying LTI platforms', () => {
        service.query({ page: 0, size: ITEMS_PER_PAGE, sort: ['id', 'desc'] }).subscribe({
            next: () => {},
            error: (error: HttpErrorResponse) => {
                expect(error.status).toBe(404);
                expect(error.error.message).toBe('No data found');
            },
        });

        const req = httpMock.expectOne('api/lti/lti-platforms?page=0&size=50&sort=id&sort=desc');
        expect(req.request.method).toBe('GET');
        req.flush({ message: 'No data found' }, { status: 404, statusText: 'Not Found' });
    });
});
