import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { SecurityFrameworkService } from 'app/programming/shared/services/security-framework.service';
import { SecurityFrameworkConfig, SecurityFrameworkVersions } from 'app/programming/shared/entities/security-framework-config.model';

describe('SecurityFrameworkService', () => {
    let service: SecurityFrameworkService;
    let httpMock: HttpTestingController;

    const resourceUrl = 'api/programming/programming-exercises/42/security-framework';
    const config = { status: 'CONFIGURED', frameworkVersion: '2.2.1' } as SecurityFrameworkConfig;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });

        service = TestBed.inject(SecurityFrameworkService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should read the selectable versions', () => {
        const versions = { selectableVersions: ['2.2.1'], templateVersions: { PLAIN_GRADLE: '2.2.1' } } as SecurityFrameworkVersions;
        let received: SecurityFrameworkVersions | undefined;

        service.getVersions().subscribe((response) => (received = response));

        httpMock.expectOne({ method: 'GET', url: 'api/programming/security-framework/versions' }).flush(versions);
        expect(received).toEqual(versions);
    });

    it('should read the config of an exercise', () => {
        let received: SecurityFrameworkConfig | undefined;

        service.getConfig(42).subscribe((response) => (received = response));

        httpMock.expectOne({ method: 'GET', url: resourceUrl }).flush(config);
        expect(received).toEqual(config);
    });

    it('should activate with the chosen version', () => {
        service.activate(42, '2.3.0').subscribe();

        const request = httpMock.expectOne({ method: 'PUT', url: `${resourceUrl}/activate?frameworkVersion=2.3.0` });
        expect(request.request.body).toBeNull();
        request.flush(config);
    });

    it('should pin another version', () => {
        service.updateFrameworkVersion(42, '2.3.0').subscribe();

        const request = httpMock.expectOne({ method: 'PUT', url: `${resourceUrl}/framework-version?frameworkVersion=2.3.0` });
        expect(request.request.body).toBeNull();
        request.flush(config);
    });

    it('should deactivate and add the discovered test classes without parameters', () => {
        service.deactivate(42).subscribe();
        service.addDiscoveredTestClasses(42).subscribe();

        const deactivation = httpMock.expectOne({ method: 'PUT', url: `${resourceUrl}/deactivate` });
        const testClasses = httpMock.expectOne({ method: 'PUT', url: `${resourceUrl}/test-classes` });
        expect(deactivation.request.params.keys()).toHaveLength(0);
        expect(testClasses.request.params.keys()).toHaveLength(0);
        deactivation.flush(config);
        testClasses.flush(config);
    });
});
