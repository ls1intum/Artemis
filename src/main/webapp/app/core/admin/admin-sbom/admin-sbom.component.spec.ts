import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { of, throwError } from 'rxjs';

import { AdminSbomComponent } from './admin-sbom.component';
import { AdminSbomService } from './admin-sbom.service';
import { AlertService } from 'app/shared/service/alert.service';
import { ArtemisVersion, CombinedSbom, ComponentVulnerabilities, SbomComponent, Vulnerability } from './admin-sbom.model';

describe('AdminSbomComponent', () => {
    setupTestBed({ zoneless: true });

    let component: AdminSbomComponent;
    let fixture: ComponentFixture<AdminSbomComponent>;
    let sbomService: AdminSbomService;
    let alertService: AlertService;

    const mockServerComponents: SbomComponent[] = [
        {
            name: 'spring-core',
            version: '6.0.0',
            type: 'library',
            group: 'org.springframework',
            purl: 'pkg:maven/org.springframework/spring-core@6.0.0',
        },
        {
            name: 'spring-boot',
            version: '3.0.0',
            type: 'library',
            group: 'org.springframework.boot',
            purl: 'pkg:maven/org.springframework.boot/spring-boot@3.0.0',
        },
    ];

    const mockClientComponents: SbomComponent[] = [
        {
            name: 'core',
            version: '17.0.0',
            type: 'library',
            group: '@angular',
            purl: 'pkg:npm/@angular/core@17.0.0',
        },
    ];

    const mockCombinedSbom: CombinedSbom = {
        server: {
            bomFormat: 'CycloneDX',
            specVersion: '1.6',
            serialNumber: 'urn:uuid:server-serial',
            version: 1,
            components: mockServerComponents,
        },
        client: {
            bomFormat: 'CycloneDX',
            specVersion: '1.6',
            serialNumber: 'urn:uuid:client-serial',
            version: 1,
            components: mockClientComponents,
        },
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
                    },
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

    const mockVersionInfo: ArtemisVersion = {
        currentVersion: '7.8.0',
        latestVersion: '7.9.0',
        updateAvailable: true,
        releaseUrl: 'https://github.com/ls1intum/Artemis/releases/tag/7.9.0',
        releaseNotes: 'New features and bug fixes',
        lastChecked: '2024-01-01T00:00:00Z',
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [AdminSbomComponent],
            providers: [
                MockProvider(AdminSbomService, {
                    getCombinedSbom: () => of(mockCombinedSbom),
                    getVulnerabilities: () => of(mockVulnerabilities),
                    refreshVulnerabilities: () => of(mockVulnerabilities),
                    getVersionInfo: () => of(mockVersionInfo),
                    sendVulnerabilityEmail: () => of(undefined),
                }),
                MockProvider(AlertService, {
                    error: vi.fn(),
                    success: vi.fn(),
                }),
            ],
        });

        fixture = TestBed.createComponent(AdminSbomComponent);
        component = fixture.componentInstance;
        sbomService = TestBed.inject(AdminSbomService);
        alertService = TestBed.inject(AlertService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    describe('loadSbom', () => {
        it('should load combined SBOM on init', () => {
            const getSbomSpy = vi.spyOn(sbomService, 'getCombinedSbom').mockReturnValue(of(mockCombinedSbom));
            const getVulnSpy = vi.spyOn(sbomService, 'getVulnerabilities').mockReturnValue(of(mockVulnerabilities));

            component.ngOnInit();

            expect(getSbomSpy).toHaveBeenCalled();
            expect(getVulnSpy).toHaveBeenCalled();
            expect(component.combinedSbom()).toEqual(mockCombinedSbom);
            expect(component.loading()).toBe(false);
        });

        it('should show error alert when SBOM load fails', () => {
            vi.spyOn(sbomService, 'getCombinedSbom').mockReturnValue(throwError(() => new Error('Load failed')));
            const errorSpy = vi.spyOn(alertService, 'error');

            component.loadSbom();

            expect(errorSpy).toHaveBeenCalledWith('artemisApp.dependencies.loadError');
            expect(component.loading()).toBe(false);
        });
    });

    describe('loadVulnerabilities', () => {
        it('should load vulnerability data', () => {
            const getVulnSpy = vi.spyOn(sbomService, 'getVulnerabilities').mockReturnValue(of(mockVulnerabilities));

            component.loadVulnerabilities();

            expect(getVulnSpy).toHaveBeenCalled();
            expect(component.vulnerabilities()).toEqual(mockVulnerabilities);
            expect(component.loadingVulnerabilities()).toBe(false);
        });

        it('should show error alert when vulnerability load fails', () => {
            vi.spyOn(sbomService, 'getVulnerabilities').mockReturnValue(throwError(() => new Error('Load failed')));
            const errorSpy = vi.spyOn(alertService, 'error');

            component.loadVulnerabilities();

            expect(errorSpy).toHaveBeenCalledWith('artemisApp.dependencies.vulnerabilityLoadError');
            expect(component.loadingVulnerabilities()).toBe(false);
        });
    });

    describe('refreshVulnerabilities', () => {
        it('should refresh vulnerability data', () => {
            const refreshSpy = vi.spyOn(sbomService, 'refreshVulnerabilities').mockReturnValue(of(mockVulnerabilities));
            const successSpy = vi.spyOn(alertService, 'success');

            component.refreshVulnerabilities();

            expect(refreshSpy).toHaveBeenCalled();
            expect(component.vulnerabilities()).toEqual(mockVulnerabilities);
            expect(successSpy).toHaveBeenCalledWith('artemisApp.dependencies.vulnerabilityRefreshSuccess');
        });
    });

    describe('filteredComponents', () => {
        beforeEach(() => {
            vi.spyOn(sbomService, 'getCombinedSbom').mockReturnValue(of(mockCombinedSbom));
            vi.spyOn(sbomService, 'getVulnerabilities').mockReturnValue(of(mockVulnerabilities));
            component.ngOnInit();
        });

        it('should return all components when no filter applied', () => {
            const components = component.filteredComponents();
            expect(components).toHaveLength(3); // 2 server + 1 client
        });

        it('should filter by source - server only', () => {
            component.updateSource('server');
            const components = component.filteredComponents();
            expect(components).toHaveLength(2);
            expect(components.every((c) => c.source === 'server')).toBe(true);
        });

        it('should filter by source - client only', () => {
            component.updateSource('client');
            const components = component.filteredComponents();
            expect(components).toHaveLength(1);
            expect(components[0].source).toBe('client');
        });

        it('should filter by text', () => {
            component.updateFilter('spring');
            const components = component.filteredComponents();
            expect(components).toHaveLength(2);
            expect(components.every((c) => c.name?.includes('spring'))).toBe(true);
        });

        it('should filter vulnerable only', () => {
            component.toggleVulnerableOnly();
            const components = component.filteredComponents();
            expect(components).toHaveLength(1);
            expect(components[0].name).toBe('spring-core');
        });
    });

    describe('component counts', () => {
        beforeEach(() => {
            vi.spyOn(sbomService, 'getCombinedSbom').mockReturnValue(of(mockCombinedSbom));
            vi.spyOn(sbomService, 'getVulnerabilities').mockReturnValue(of(mockVulnerabilities));
            component.ngOnInit();
        });

        it('should correctly count server components', () => {
            expect(component.serverComponentCount()).toBe(2);
        });

        it('should correctly count client components', () => {
            expect(component.clientComponentCount()).toBe(1);
        });

        it('should correctly count total components', () => {
            expect(component.totalComponentCount()).toBe(3);
        });
    });

    describe('sorting', () => {
        beforeEach(() => {
            vi.spyOn(sbomService, 'getCombinedSbom').mockReturnValue(of(mockCombinedSbom));
            vi.spyOn(sbomService, 'getVulnerabilities').mockReturnValue(of(mockVulnerabilities));
            component.ngOnInit();
        });

        it('should sort by name ascending by default', () => {
            const components = component.filteredComponents();
            expect(components[0].name).toBe('core');
            expect(components[1].name).toBe('spring-boot');
            expect(components[2].name).toBe('spring-core');
        });

        it('should toggle sort direction when clicking same field', () => {
            expect(component.sortAscending()).toBe(true);
            component.updateSortField('name');
            expect(component.sortAscending()).toBe(false);
        });

        it('should reset to ascending when clicking different field', () => {
            component.updateSortField('name'); // Toggle to descending
            component.updateSortField('version');
            expect(component.sortField()).toBe('version');
            expect(component.sortAscending()).toBe(true);
        });
    });

    describe('getHighestSeverity', () => {
        it('should return empty string for no vulnerabilities', () => {
            expect(component.getHighestSeverity(undefined)).toBe('');
            expect(component.getHighestSeverity([])).toBe('');
        });

        it('should return CRITICAL when present', () => {
            const vulns: Vulnerability[] = [{ severity: 'HIGH' } as Vulnerability, { severity: 'CRITICAL' } as Vulnerability];
            expect(component.getHighestSeverity(vulns)).toBe('CRITICAL');
        });

        it('should return HIGH when no CRITICAL', () => {
            const vulns: Vulnerability[] = [{ severity: 'HIGH' } as Vulnerability, { severity: 'LOW' } as Vulnerability];
            expect(component.getHighestSeverity(vulns)).toBe('HIGH');
        });
    });

    describe('getSeverityClass', () => {
        it('should return bg-danger for CRITICAL', () => {
            expect(component.getSeverityClass('CRITICAL')).toBe('bg-danger');
        });

        it('should return bg-warning for HIGH', () => {
            expect(component.getSeverityClass('HIGH')).toBe('bg-warning text-dark');
        });

        it('should return bg-info for LOW', () => {
            expect(component.getSeverityClass('LOW')).toBe('bg-info');
        });

        it('should return bg-secondary for unknown', () => {
            expect(component.getSeverityClass('UNKNOWN')).toBe('bg-secondary');
        });
    });

    describe('sendVulnerabilityEmail', () => {
        it('should send vulnerability email and show success alert', () => {
            const sendEmailSpy = vi.spyOn(sbomService, 'sendVulnerabilityEmail').mockReturnValue(of(undefined));
            const successSpy = vi.spyOn(alertService, 'success');

            component.sendVulnerabilityEmail();

            expect(sendEmailSpy).toHaveBeenCalled();
            expect(successSpy).toHaveBeenCalledWith('artemisApp.dependencies.emailSentSuccess');
            expect(component.sendingEmail()).toBe(false);
        });

        it('should show error alert when sending email fails', () => {
            vi.spyOn(sbomService, 'sendVulnerabilityEmail').mockReturnValue(throwError(() => new Error('Send failed')));
            const errorSpy = vi.spyOn(alertService, 'error');

            component.sendVulnerabilityEmail();

            expect(errorSpy).toHaveBeenCalledWith('artemisApp.dependencies.emailSentError');
            expect(component.sendingEmail()).toBe(false);
        });

        it('should set sendingEmail to true while sending', () => {
            // Use a subject to control when the observable completes
            const sendEmailSpy = vi.spyOn(sbomService, 'sendVulnerabilityEmail').mockReturnValue(of(undefined));

            expect(component.sendingEmail()).toBe(false);
            component.sendVulnerabilityEmail();
            // After completion, sendingEmail should be false again
            expect(component.sendingEmail()).toBe(false);
            expect(sendEmailSpy).toHaveBeenCalled();
        });
    });
});
