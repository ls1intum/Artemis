/**
 * Represents a component from a CycloneDX SBOM.
 */
export interface SbomComponent {
    group?: string;
    name: string;
    version: string;
    type?: string;
    purl?: string;
    licenses?: string[];
    description?: string;
}

/**
 * Metadata from a CycloneDX SBOM.
 */
export interface SbomMetadata {
    timestamp?: string;
    componentName?: string;
    version?: string;
}

/**
 * Represents a Software Bill of Materials (SBOM).
 */
export interface Sbom {
    bomFormat?: string;
    specVersion?: string;
    serialNumber?: string;
    version?: number;
    metadata?: SbomMetadata;
    components: SbomComponent[];
}

/**
 * Combined SBOM containing both server and client dependencies.
 */
export interface CombinedSbom {
    server?: Sbom;
    client?: Sbom;
}

/**
 * Represents a security vulnerability from OSV database.
 */
export interface Vulnerability {
    id: string;
    summary?: string;
    details?: string;
    severity: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'UNKNOWN';
    cvssScore?: number;
    aliases?: string[];
    fixedIn?: string[];
    references?: string[];
}

/**
 * Represents a component and its associated vulnerabilities.
 */
export interface ComponentWithVulnerabilities {
    componentKey: string;
    vulnerabilities: Vulnerability[];
}

/**
 * Vulnerability information for all components.
 */
export interface ComponentVulnerabilities {
    vulnerabilities: ComponentWithVulnerabilities[];
    totalVulnerabilities: number;
    criticalCount: number;
    highCount: number;
    mediumCount: number;
    lowCount: number;
    lastChecked: string;
}

/**
 * Artemis version information including update status.
 */
export interface ArtemisVersion {
    currentVersion: string;
    latestVersion?: string;
    updateAvailable: boolean;
    releaseUrl?: string;
    releaseNotes?: string;
    lastChecked: string;
}
