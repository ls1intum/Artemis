import { beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { AdminSbomService } from './admin-sbom.service';
import { CombinedSbom, ComponentVulnerabilities, Sbom, SbomComponent, Vulnerability } from './admin-sbom.model';

describe('AdminSbomService', () => {
    setupTestBed({ zoneless: true });

    let service: AdminSbomService;
    let httpMock: HttpTestingController;

    const mockServerSbom: Sbom = {
        bomFormat: 'CycloneDX',
        specVersion: '1.6',
        serialNumber: 'urn:uuid:server-serial',
        version: 1,
        components: [
            {
                name: 'spring-core',
                version: '6.0.0',
                type: 'library',
                group: 'org.springframework',
                purl: 'pkg:maven/org.springframework/spring-core@6.0.0',
            } as SbomComponent,
        ],
    };

    const mockClientSbom: Sbom = {
        bomFormat: 'CycloneDX',
        specVersion: '1.6',
        serialNumber: 'urn:uuid:client-serial',
        version: 1,
        components: [
            {
                name: 'core',
                version: '17.0.0',
                type: 'library',
                group: '@angular',
                purl: 'pkg:npm/@angular/core@17.0.0',
            } as SbomComponent,
        ],
    };

    const mockCombinedSbom: CombinedSbom = {
        server: mockServerSbom,
        client: mockClientSbom,
    };

    const mockVulnerabilities: ComponentVulnerabilities = {
        vulnerabilities: [
            {
                componentKey: 'pkg:maven/org.springframework/spring-core@6.0.0',
                vulnerabilities: [
                    {
                        id: 'CVE-2024-1234',
                        summary: 'Test vulnerability',
                        details: 'Test details',
                        severity: 'HIGH',
                        cvssScore: 7.5,
                        fixedIn: ['6.1.0'],
                        references: ['https://example.com'],
                    } as Vulnerability,
                ],
            },
        ],
        totalVulnerabilities: 1,
        criticalCount: 0,
        highCount: 1,
        mediumCount: 0,
        lowCount: 0,
        lastChecked: '2024-01-01T00:00:00Z',
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [AdminSbomService, provideHttpClient(), provideHttpClientTesting()],
        });

        service = TestBed.inject(AdminSbomService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    describe('getCombinedSbom', () => {
        it('should return combined SBOM', () => {
            service.getCombinedSbom().subscribe((result) => {
                expect(result).toEqual(mockCombinedSbom);
                expect(result.server?.bomFormat).toBe('CycloneDX');
                expect(result.client?.bomFormat).toBe('CycloneDX');
            });

            const req = httpMock.expectOne('api/core/admin/sbom');
            expect(req.request.method).toBe('GET');
            req.flush(mockCombinedSbom);
        });
    });

    describe('getServerSbom', () => {
        it('should return server SBOM', () => {
            service.getServerSbom().subscribe((result) => {
                expect(result).toEqual(mockServerSbom);
                expect(result.components).toHaveLength(1);
                expect(result.components![0].name).toBe('spring-core');
            });

            const req = httpMock.expectOne('api/core/admin/sbom/server');
            expect(req.request.method).toBe('GET');
            req.flush(mockServerSbom);
        });
    });

    describe('getClientSbom', () => {
        it('should return client SBOM', () => {
            service.getClientSbom().subscribe((result) => {
                expect(result).toEqual(mockClientSbom);
                expect(result.components).toHaveLength(1);
                expect(result.components![0].name).toBe('core');
            });

            const req = httpMock.expectOne('api/core/admin/sbom/client');
            expect(req.request.method).toBe('GET');
            req.flush(mockClientSbom);
        });
    });

    describe('getVulnerabilities', () => {
        it('should return vulnerability data', () => {
            service.getVulnerabilities().subscribe((result) => {
                expect(result).toEqual(mockVulnerabilities);
                expect(result.totalVulnerabilities).toBe(1);
                expect(result.highCount).toBe(1);
            });

            const req = httpMock.expectOne('api/core/admin/sbom/vulnerabilities');
            expect(req.request.method).toBe('GET');
            req.flush(mockVulnerabilities);
        });
    });

    describe('refreshVulnerabilities', () => {
        it('should return refreshed vulnerability data', () => {
            service.refreshVulnerabilities().subscribe((result) => {
                expect(result).toEqual(mockVulnerabilities);
            });

            const req = httpMock.expectOne('api/core/admin/sbom/vulnerabilities/refresh');
            expect(req.request.method).toBe('GET');
            req.flush(mockVulnerabilities);
        });
    });
});
