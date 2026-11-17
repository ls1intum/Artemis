import { Service, inject } from '@angular/core';
import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SKIP_HTTP_ERROR_ALERT } from 'app/core/interceptor/errorhandler.interceptor';
import { SecurityFrameworkConfig, SecurityFrameworkVersions } from 'app/programming/shared/entities/security-framework-config.model';
import {
    SecurityPolicyChange,
    SecurityPolicyCommitResult,
    SecurityPolicyEditor,
    SecurityPolicyFile,
    SecurityPolicyPreview,
    SecurityTemplateFiles,
    SecurityTestStructure,
} from 'app/programming/shared/entities/security-policy.model';

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

    /** The Ares versions an instructor can pin and the version each template starts with. */
    getVersions(): Observable<SecurityFrameworkVersions> {
        return this.http.get<SecurityFrameworkVersions>('api/programming/security-framework/versions');
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

    /** Adds the test classes that are missing from the policy files (decision B5); the server never removes an entry. */
    addDiscoveredTestClasses(exerciseId: number): Observable<SecurityFrameworkConfig> {
        return this.http.put<SecurityFrameworkConfig>(`${this.resourceUrl(exerciseId)}/test-classes`, null);
    }

    /** Pins another Ares version while active. */
    updateFrameworkVersion(exerciseId: number, frameworkVersion: string): Observable<SecurityFrameworkConfig> {
        return this.http.put<SecurityFrameworkConfig>(`${this.resourceUrl(exerciseId)}/framework-version`, null, {
            params: new HttpParams().set('frameworkVersion', frameworkVersion),
        });
    }

    /** The security policies of an exercise, with the commit of the tests repository they were read from. */
    getPolicies(exerciseId: number): Observable<SecurityPolicyEditor> {
        return this.http.get<SecurityPolicyEditor>(`${this.resourceUrl(exerciseId)}/policies`);
    }

    /**
     * Renders changed policies without committing them and checks them against the pinned Ares version. The editor shows a failure itself, like the
     * failures of committing and importing, so no error alert repeats it.
     */
    previewPolicies(exerciseId: number, change: SecurityPolicyChange): Observable<SecurityPolicyPreview> {
        return this.http.post<SecurityPolicyPreview>(`${this.resourceUrl(exerciseId)}/policies/preview`, change, { context: this.shownByEditor() });
    }

    /** Commits changed policies to the tests repository; fails with 409 if the policies changed since they were loaded. */
    commitPolicies(exerciseId: number, change: SecurityPolicyChange): Observable<SecurityPolicyCommitResult> {
        return this.http.put<SecurityPolicyCommitResult>(`${this.resourceUrl(exerciseId)}/policies`, change, { context: this.shownByEditor() });
    }

    /** Reads an imported policy file like a committed one, without writing it. */
    importPolicy(exerciseId: number, policyPath: string, content: string): Observable<SecurityPolicyFile> {
        return this.http.post<SecurityPolicyFile>(`${this.resourceUrl(exerciseId)}/policies/import`, { policyPath, content }, { context: this.shownByEditor() });
    }

    /** The test classes of the tests repository with their test cases. */
    getTestStructure(exerciseId: number): Observable<SecurityTestStructure> {
        return this.http.get<SecurityTestStructure>(`${this.resourceUrl(exerciseId)}/test-structure`);
    }

    /** The files of the template repository. */
    getTemplateFiles(exerciseId: number): Observable<SecurityTemplateFiles> {
        return this.http.get<SecurityTemplateFiles>(`${this.resourceUrl(exerciseId)}/template-files`);
    }

    /** The context of a request whose failure the policy editor shows next to what failed. */
    private shownByEditor(): HttpContext {
        return new HttpContext().set(SKIP_HTTP_ERROR_ALERT, true);
    }
}
