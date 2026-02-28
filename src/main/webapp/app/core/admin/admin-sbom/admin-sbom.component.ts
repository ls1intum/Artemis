import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import {
    faArrowUp,
    faDesktop,
    faDownload,
    faEnvelope,
    faExclamationTriangle,
    faExternalLinkAlt,
    faInfoCircle,
    faRefresh,
    faSearch,
    faServer,
    faShieldAlt,
    faSort,
    faSpinner,
} from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgClass } from '@angular/common';

import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { AdminTitleBarTitleDirective } from 'app/core/admin/shared/admin-title-bar-title.directive';
import { AdminTitleBarActionsDirective } from 'app/core/admin/shared/admin-title-bar-actions.directive';
import { AlertService } from 'app/shared/service/alert.service';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';

import { AdminSbomService } from './admin-sbom.service';
import { ArtemisVersion, CombinedSbom, ComponentVulnerabilities, SbomComponent, Vulnerability } from './admin-sbom.model';

type SbomSource = 'all' | 'server' | 'client';

/**
 * Admin component for viewing Software Bill of Materials (SBOM).
 * Displays server (Java/Gradle) and client (npm) dependencies with filtering and sorting.
 */
@Component({
    selector: 'jhi-admin-sbom',
    templateUrl: './admin-sbom.component.html',
    styleUrls: ['./admin-sbom.component.scss'],
    imports: [
        TranslateDirective,
        FormsModule,
        FaIconComponent,
        AdminTitleBarTitleDirective,
        AdminTitleBarActionsDirective,
        ArtemisTranslatePipe,
        SortDirective,
        NgClass,
        HelpIconComponent,
    ],
})
export class AdminSbomComponent implements OnInit {
    private readonly sbomService = inject(AdminSbomService);
    private readonly alertService = inject(AlertService);

    // Icons
    protected readonly faSort = faSort;
    protected readonly faSearch = faSearch;
    protected readonly faSpinner = faSpinner;
    protected readonly faServer = faServer;
    protected readonly faDesktop = faDesktop;
    protected readonly faDownload = faDownload;
    protected readonly faShieldAlt = faShieldAlt;
    protected readonly faExclamationTriangle = faExclamationTriangle;
    protected readonly faRefresh = faRefresh;
    protected readonly faArrowUp = faArrowUp;
    protected readonly faInfoCircle = faInfoCircle;
    protected readonly faExternalLink = faExternalLinkAlt;
    protected readonly faEnvelope = faEnvelope;

    // State
    readonly loading = signal<boolean>(false);
    readonly loadingVulnerabilities = signal<boolean>(false);
    readonly sendingEmail = signal<boolean>(false);
    readonly combinedSbom = signal<CombinedSbom | undefined>(undefined);
    readonly vulnerabilities = signal<ComponentVulnerabilities | undefined>(undefined);
    readonly versionInfo = signal<ArtemisVersion | undefined>(undefined);
    readonly filterText = signal<string>('');
    readonly selectedSource = signal<SbomSource>('all');
    readonly showOnlyVulnerable = signal<boolean>(false);
    readonly sortAscending = signal<boolean>(true);
    readonly sortField = signal<'name' | 'group' | 'version' | 'type'>('name');

    // Computed values
    readonly serverComponentCount = computed(() => this.combinedSbom()?.server?.components?.length ?? 0);
    readonly clientComponentCount = computed(() => this.combinedSbom()?.client?.components?.length ?? 0);
    readonly totalComponentCount = computed(() => this.serverComponentCount() + this.clientComponentCount());

    readonly serverMetadata = computed(() => this.combinedSbom()?.server?.metadata);
    readonly clientMetadata = computed(() => this.combinedSbom()?.client?.metadata);

    /**
     * Returns true if there are critical or high severity vulnerabilities.
     */
    readonly hasUrgentVulnerabilities = computed(() => {
        const vulns = this.vulnerabilities();
        return vulns ? vulns.criticalCount > 0 || vulns.highCount > 0 : false;
    });

    /**
     * Returns the urgency level for upgrade recommendations.
     */
    readonly upgradeUrgency = computed(() => {
        const vulns = this.vulnerabilities();
        const version = this.versionInfo();
        if (!version?.updateAvailable) {
            return 'none';
        }
        if (vulns?.criticalCount && vulns.criticalCount > 0) {
            return 'critical';
        }
        if (vulns?.highCount && vulns.highCount > 0) {
            return 'high';
        }
        return 'normal';
    });

    /**
     * Filtered and sorted components based on current filter and sort settings.
     */
    readonly filteredComponents = computed(() => {
        const sbom = this.combinedSbom();
        if (!sbom) {
            return [];
        }

        const vulnData = this.vulnerabilities();
        let components: (SbomComponent & { source: 'server' | 'client'; componentVulnerabilities?: Vulnerability[] })[] = [];

        // Collect components based on selected source
        const source = this.selectedSource();
        if ((source === 'all' || source === 'server') && sbom.server?.components) {
            components = components.concat(
                sbom.server.components.map((c) => ({
                    ...c,
                    source: 'server' as const,
                    componentVulnerabilities: this.getComponentVulnerabilities(c, vulnData),
                })),
            );
        }
        if ((source === 'all' || source === 'client') && sbom.client?.components) {
            components = components.concat(
                sbom.client.components.map((c) => ({
                    ...c,
                    source: 'client' as const,
                    componentVulnerabilities: this.getComponentVulnerabilities(c, vulnData),
                })),
            );
        }

        // Apply vulnerable-only filter
        if (this.showOnlyVulnerable()) {
            components = components.filter((c) => c.componentVulnerabilities && c.componentVulnerabilities.length > 0);
        }

        // Apply text filter
        const filter = this.filterText().toLowerCase();
        if (filter) {
            components = components.filter(
                (c) =>
                    c.name?.toLowerCase().includes(filter) ||
                    c.group?.toLowerCase().includes(filter) ||
                    c.version?.toLowerCase().includes(filter) ||
                    c.type?.toLowerCase().includes(filter) ||
                    c.licenses?.some((l) => l.toLowerCase().includes(filter)),
            );
        }

        // Apply sorting
        const field = this.sortField();
        const ascending = this.sortAscending();
        const multiplier = ascending ? 1 : -1;

        components.sort((a, b) => {
            const aVal = (a[field] ?? '').toLowerCase();
            const bVal = (b[field] ?? '').toLowerCase();
            return aVal.localeCompare(bVal) * multiplier;
        });

        return components;
    });

    /**
     * Get vulnerabilities for a specific component.
     */
    private getComponentVulnerabilities(component: SbomComponent, vulnData: ComponentVulnerabilities | undefined): Vulnerability[] | undefined {
        if (!vulnData?.vulnerabilities || vulnData.vulnerabilities.length === 0) {
            return undefined;
        }

        // Try to find by purl first
        if (component.purl) {
            const byPurl = vulnData.vulnerabilities.find((v) => v.componentKey === component.purl);
            if (byPurl) {
                return byPurl.vulnerabilities;
            }
        }

        // Try synthetic key format
        const ecosystem = this.determineEcosystem(component);
        if (ecosystem) {
            const syntheticKey = `${ecosystem}:${component.group ?? ''}/${component.name}@${component.version}`;
            const bySyntheticKey = vulnData.vulnerabilities.find((v) => v.componentKey === syntheticKey);
            if (bySyntheticKey) {
                return bySyntheticKey.vulnerabilities;
            }
        }

        return undefined;
    }

    /**
     * Determine the ecosystem for a component.
     */
    private determineEcosystem(component: SbomComponent): string | undefined {
        if (component.purl) {
            if (component.purl.startsWith('pkg:maven/')) {
                return 'Maven';
            }
            if (component.purl.startsWith('pkg:npm/')) {
                return 'npm';
            }
        }

        // Heuristic based on group
        if (component.group) {
            if (component.group.includes('.') || component.group.startsWith('org.') || component.group.startsWith('com.')) {
                return 'Maven';
            }
            if (component.group.startsWith('@')) {
                return 'npm';
            }
        }

        return undefined;
    }

    ngOnInit(): void {
        this.loadSbom();
        this.loadVersionInfo();
    }

    /**
     * Loads the combined SBOM data from the server.
     */
    loadSbom(): void {
        this.loading.set(true);
        this.sbomService.getCombinedSbom().subscribe({
            next: (sbom) => {
                this.combinedSbom.set(sbom);
                this.loading.set(false);
                // Load vulnerabilities after SBOM is loaded
                this.loadVulnerabilities();
            },
            error: () => {
                this.alertService.error('artemisApp.dependencies.loadError');
                this.loading.set(false);
            },
        });
    }

    /**
     * Loads vulnerability data for all components.
     */
    loadVulnerabilities(): void {
        this.loadingVulnerabilities.set(true);
        this.sbomService.getVulnerabilities().subscribe({
            next: (vulnData) => {
                this.vulnerabilities.set(vulnData);
                this.loadingVulnerabilities.set(false);
            },
            error: () => {
                this.alertService.error('artemisApp.dependencies.vulnerabilityLoadError');
                this.loadingVulnerabilities.set(false);
            },
        });
    }

    /**
     * Loads Artemis version information.
     */
    loadVersionInfo(): void {
        this.sbomService.getVersionInfo().subscribe({
            next: (version) => {
                this.versionInfo.set(version);
            },
            error: () => {
                // Silently fail - version info is optional
            },
        });
    }

    /**
     * Refresh vulnerability data from OSV API.
     */
    refreshVulnerabilities(): void {
        this.loadingVulnerabilities.set(true);
        this.sbomService.refreshVulnerabilities().subscribe({
            next: (vulnData) => {
                this.vulnerabilities.set(vulnData);
                this.loadingVulnerabilities.set(false);
                this.alertService.success('artemisApp.dependencies.vulnerabilityRefreshSuccess');
            },
            error: () => {
                this.alertService.error('artemisApp.dependencies.vulnerabilityLoadError');
                this.loadingVulnerabilities.set(false);
            },
        });
    }

    /**
     * Send vulnerability report email to the configured admin.
     */
    sendVulnerabilityEmail(): void {
        this.sendingEmail.set(true);
        this.sbomService.sendVulnerabilityEmail().subscribe({
            next: () => {
                this.sendingEmail.set(false);
                this.alertService.success('artemisApp.dependencies.emailSentSuccess');
            },
            error: () => {
                this.alertService.error('artemisApp.dependencies.emailSentError');
                this.sendingEmail.set(false);
            },
        });
    }

    /**
     * Toggle showing only vulnerable packages.
     */
    toggleVulnerableOnly(): void {
        this.showOnlyVulnerable.set(!this.showOnlyVulnerable());
    }

    /**
     * Get the highest severity from a list of vulnerabilities.
     */
    getHighestSeverity(vulns: Vulnerability[] | undefined): string {
        if (!vulns || vulns.length === 0) {
            return '';
        }

        const severityOrder = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'UNKNOWN'];
        for (const severity of severityOrder) {
            if (vulns.some((v) => v.severity === severity)) {
                return severity;
            }
        }
        return 'UNKNOWN';
    }

    /**
     * Get CSS class for severity badge.
     */
    getSeverityClass(severity: string): string {
        switch (severity) {
            case 'CRITICAL':
                return 'bg-danger';
            case 'HIGH':
                return 'bg-warning text-dark';
            case 'MEDIUM':
                return 'bg-warning text-dark';
            case 'LOW':
                return 'bg-info';
            default:
                return 'bg-secondary';
        }
    }

    /**
     * Updates the filter text.
     */
    updateFilter(value: string): void {
        this.filterText.set(value);
    }

    /**
     * Updates the selected source filter.
     */
    updateSource(source: SbomSource): void {
        this.selectedSource.set(source);
    }

    /**
     * Updates the sort direction.
     */
    updateSortAscending(ascending: boolean): void {
        this.sortAscending.set(ascending);
    }

    /**
     * Updates the sort field.
     */
    updateSortField(field: 'name' | 'group' | 'version' | 'type'): void {
        if (this.sortField() === field) {
            this.sortAscending.set(!this.sortAscending());
        } else {
            this.sortField.set(field);
            this.sortAscending.set(true);
        }
    }

    /**
     * Downloads the raw SBOM as JSON.
     */
    downloadSbom(source: 'server' | 'client'): void {
        const sbom = source === 'server' ? this.combinedSbom()?.server : this.combinedSbom()?.client;
        if (!sbom) {
            return;
        }

        const blob = new Blob([JSON.stringify(sbom, undefined, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `${source}-sbom.json`;
        a.click();
        URL.revokeObjectURL(url);
    }

    /**
     * Track function for ngFor.
     */
    trackByName(_index: number, component: SbomComponent): string {
        return `${component.group ?? ''}:${component.name}:${component.version}`;
    }
}
