import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SecurityActivationStatus, SecurityFrameworkConfig, SecurityFrameworkVersionOption } from 'app/programming/shared/entities/security-framework-config.model';

/**
 * Framework releases selectable in the "Framework Version" dropdown. Kept client-side so the control can
 * render synchronously; the server independently validates the chosen version against the (mocked) Ares2
 * compatibility service, and exposes the same list at `.../security-framework/supported-versions`.
 */
const FRAMEWORK_VERSIONS: SecurityFrameworkVersionOption[] = [
    { version: '3.4.1', label: '3.4.1', latest: true },
    { version: '3.3.0', label: '3.3.0' },
    { version: '3.2.2', label: '3.2.2' },
];

/**
 * Client for the Security Framework activation state of a programming exercise (Objective 1).
 *
 * Talks to the Artemis server resource `api/programming/programming-exercises/{exerciseId}/security-framework`.
 * The server generates/commits the policy (via the mocked Ares2 integration) and returns the settled config;
 * the component owns the transient GENERATING/DELETING states while these calls are in flight.
 */
@Injectable({ providedIn: 'root' })
export class SecurityFrameworkService {
    private readonly http = inject(HttpClient);

    private resourceUrl(exerciseId: number): string {
        return `api/programming/programming-exercises/${exerciseId}/security-framework`;
    }

    /** Framework releases for the dropdown. */
    getFrameworkVersions(): SecurityFrameworkVersionOption[] {
        return FRAMEWORK_VERSIONS;
    }

    /** Initial state for a not-yet-created exercise: off, nothing committed (staged locally until the exercise exists). */
    getInitialConfigForCreate(): SecurityFrameworkConfig {
        return { status: SecurityActivationStatus.INACTIVE, frameworkVersion: FRAMEWORK_VERSIONS[0].version };
    }

    /** Loads the persisted config of an existing exercise. */
    getConfig(exerciseId: number): Observable<SecurityFrameworkConfig> {
        return this.http.get<SecurityFrameworkConfig>(this.resourceUrl(exerciseId));
    }

    /** Activates the sandbox: the server generates the implicit default policy and commits it. */
    activate(exerciseId: number, frameworkVersion: string): Observable<SecurityFrameworkConfig> {
        return this.http.put<SecurityFrameworkConfig>(`${this.resourceUrl(exerciseId)}/activate`, null, {
            params: new HttpParams().set('frameworkVersion', frameworkVersion),
        });
    }

    /** Deactivates the sandbox: the server removes the policy from the exercise-tests repository. */
    deactivate(exerciseId: number): Observable<SecurityFrameworkConfig> {
        return this.http.put<SecurityFrameworkConfig>(`${this.resourceUrl(exerciseId)}/deactivate`, null);
    }

    /** Switches the framework version while active (the server re-generates and re-commits the policy). */
    updateFrameworkVersion(exerciseId: number, frameworkVersion: string): Observable<SecurityFrameworkConfig> {
        return this.http.put<SecurityFrameworkConfig>(`${this.resourceUrl(exerciseId)}/framework-version`, null, {
            params: new HttpParams().set('frameworkVersion', frameworkVersion),
        });
    }
}
