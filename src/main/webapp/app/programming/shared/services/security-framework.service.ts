import { Service, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SecurityFrameworkConfig } from 'app/programming/shared/entities/security-framework-config.model';

/**
 * Client for the Security Framework (Ares 2) of a programming exercise.
 *
 * Talks to the Artemis server resource `api/programming/programming-exercises/{exerciseId}/security-framework`. The server
 * commits each change to the exercise's tests repository and returns the settled config; the component owns the transient
 * GENERATING/DELETING states while these calls are in flight.
 */
@Service()
export class SecurityFrameworkService {
    private readonly http = inject(HttpClient);

    private resourceUrl(exerciseId: number): string {
        return `api/programming/programming-exercises/${exerciseId}/security-framework`;
    }

    /** The Ares versions an instructor can pin, newest first. */
    getSupportedVersions(): Observable<string[]> {
        return this.http.get<string[]>('api/programming/security-framework/supported-versions');
    }

    /** Reads the config of an existing exercise from its tests repository. */
    getConfig(exerciseId: number): Observable<SecurityFrameworkConfig> {
        return this.http.get<SecurityFrameworkConfig>(this.resourceUrl(exerciseId));
    }

    /** Activates the sandbox: the server commits the security policy and the given Ares version to the tests repository. */
    activate(exerciseId: number, frameworkVersion: string): Observable<SecurityFrameworkConfig> {
        return this.http.put<SecurityFrameworkConfig>(`${this.resourceUrl(exerciseId)}/activate`, null, {
            params: new HttpParams().set('frameworkVersion', frameworkVersion),
        });
    }

    /** Deactivates the sandbox: the server removes the security policy from the tests repository. */
    deactivate(exerciseId: number): Observable<SecurityFrameworkConfig> {
        return this.http.put<SecurityFrameworkConfig>(`${this.resourceUrl(exerciseId)}/deactivate`, null);
    }

    /** Pins another Ares version while active. */
    updateFrameworkVersion(exerciseId: number, frameworkVersion: string): Observable<SecurityFrameworkConfig> {
        return this.http.put<SecurityFrameworkConfig>(`${this.resourceUrl(exerciseId)}/framework-version`, null, {
            params: new HttpParams().set('frameworkVersion', frameworkVersion),
        });
    }
}
