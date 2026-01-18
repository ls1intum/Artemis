import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ArtemisVersion, CombinedSbom, ComponentVulnerabilities, Sbom } from './admin-sbom.model';

/**
 * Service for fetching Software Bill of Materials (SBOM) data from the server.
 */
@Injectable({ providedIn: 'root' })
export class AdminSbomService {
    private readonly http = inject(HttpClient);
    private readonly resourceUrl = '/api/core/admin/sbom';

    /**
     * Retrieves the combined SBOM containing both server and client dependencies.
     *
     * @returns Observable with the combined SBOM data
     */
    getCombinedSbom(): Observable<CombinedSbom> {
        return this.http.get<CombinedSbom>(this.resourceUrl);
    }

    /**
     * Retrieves the server-side SBOM (Java/Gradle dependencies).
     *
     * @returns Observable with the server SBOM data
     */
    getServerSbom(): Observable<Sbom> {
        return this.http.get<Sbom>(`${this.resourceUrl}/server`);
    }

    /**
     * Retrieves the client-side SBOM (npm dependencies).
     *
     * @returns Observable with the client SBOM data
     */
    getClientSbom(): Observable<Sbom> {
        return this.http.get<Sbom>(`${this.resourceUrl}/client`);
    }

    /**
     * Retrieves vulnerability information for all SBOM components.
     * Results are cached on the server for 24 hours.
     *
     * @returns Observable with vulnerability data
     */
    getVulnerabilities(): Observable<ComponentVulnerabilities> {
        return this.http.get<ComponentVulnerabilities>(`${this.resourceUrl}/vulnerabilities`);
    }

    /**
     * Force refresh vulnerability information from OSV API, bypassing cache.
     *
     * @returns Observable with fresh vulnerability data
     */
    refreshVulnerabilities(): Observable<ComponentVulnerabilities> {
        return this.http.get<ComponentVulnerabilities>(`${this.resourceUrl}/vulnerabilities/refresh`);
    }

    /**
     * Retrieves Artemis version information including update availability.
     *
     * @returns Observable with version info
     */
    getVersionInfo(): Observable<ArtemisVersion> {
        return this.http.get<ArtemisVersion>(`${this.resourceUrl}/version`);
    }
}
