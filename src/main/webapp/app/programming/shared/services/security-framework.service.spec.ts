import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { SKIP_HTTP_ERROR_ALERT } from 'app/core/interceptor/errorhandler.interceptor';
import { SecurityPolicyChange } from 'app/programming/shared/entities/security-policy.model';
import { SecurityFrameworkService } from 'app/programming/shared/services/security-framework.service';
import { SecurityFrameworkConfig, SecurityFrameworkVersions } from 'app/programming/shared/entities/security-framework-config.model';

const URL = 'api/programming/programming-exercises/42/security-framework';
const config = { status: 'CONFIGURED', frameworkVersion: '2.2.1' } as SecurityFrameworkConfig;

describe('SecurityFrameworkService', () => {
    let service: SecurityFrameworkService;
    let http: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        service = TestBed.inject(SecurityFrameworkService);
        http = TestBed.inject(HttpTestingController);
    });

    afterEach(() => http.verify());

    it('reads the state and the versions and changes the activation', () => {
        service.getConfig(42).subscribe();
        service.getVersions().subscribe();
        service.activate(42, '2.2.1').subscribe();
        service.deactivate(42).subscribe();
        service.addDiscoveredTestClasses(42).subscribe();
        service.updateFrameworkVersion(42, '2.2.1').subscribe();

        expect(http.expectOne({ method: 'GET', url: URL })).toBeDefined();
        expect(http.expectOne({ method: 'GET', url: 'api/programming/security-framework/versions' })).toBeDefined();
        expect(http.expectOne((request) => request.method === 'PUT' && request.url === `${URL}/activate`).request.params.get('frameworkVersion')).toBe('2.2.1');
        expect(http.expectOne({ method: 'PUT', url: `${URL}/deactivate` })).toBeDefined();
        expect(http.expectOne({ method: 'PUT', url: `${URL}/test-classes` })).toBeDefined();
        expect(http.expectOne((request) => request.method === 'PUT' && request.url === `${URL}/framework-version`).request.params.get('frameworkVersion')).toBe('2.2.1');
    });

    it('reads the policies, the test structure and the template files', () => {
        service.getPolicies(42).subscribe();
        service.getTestStructure(42).subscribe();
        service.getTemplateFiles(42).subscribe();

        expect(http.expectOne({ method: 'GET', url: `${URL}/policies` })).toBeDefined();
        expect(http.expectOne({ method: 'GET', url: `${URL}/test-structure` })).toBeDefined();
        expect(http.expectOne({ method: 'GET', url: `${URL}/template-files` })).toBeDefined();

        // The review reads the test cases of the commit the policies were loaded from.
        service.getTestStructure(42, 'abc').subscribe();
        expect(http.expectOne((request) => request.method === 'GET' && request.url === `${URL}/test-structure` && request.params.get('commit') === 'abc')).toBeDefined();

        // The recovery after a pushed but unprocessed commit reads the policies and the state of one commit.
        service.getPoliciesWithConfig(42).subscribe();
        expect(http.expectOne({ method: 'GET', url: `${URL}/policies-with-config` })).toBeDefined();
    });

    it('previews and commits a change and imports a file', () => {
        const change: SecurityPolicyChange = { baseCommitHash: 'abc', commitMessage: 'Update', policies: [], removedPolicyPaths: ['Old.yaml'] };

        service.previewPolicies(42, change).subscribe();
        service.commitPolicies(42, change).subscribe();
        service.importPolicy(42, 'SecurityPolicy.yaml', 'content').subscribe();

        const preview = http.expectOne({ method: 'POST', url: `${URL}/policies/preview` }).request;
        const commit = http.expectOne({ method: 'PUT', url: `${URL}/policies` }).request;
        const imported = http.expectOne({ method: 'POST', url: `${URL}/policies/import` }).request;
        expect(preview.body).toEqual(change);
        expect(commit.body).toEqual(change);
        expect(imported.body).toEqual({ policyPath: 'SecurityPolicy.yaml', content: 'content' });
        // The editor shows these failures itself, e.g. a conflict as its banner, so no error alert repeats them.
        expect([preview, commit, imported].map((request) => request.context.get(SKIP_HTTP_ERROR_ALERT))).toEqual([true, true, true]);
    });

    it('should read the selectable versions', () => {
        const versions = { selectableVersions: ['2.2.1'], templateVersions: { PLAIN_GRADLE: '2.2.1' } } as SecurityFrameworkVersions;
        let received: SecurityFrameworkVersions | undefined;

        service.getVersions().subscribe((response) => (received = response));

        http.expectOne({ method: 'GET', url: 'api/programming/security-framework/versions' }).flush(versions);
        expect(received).toEqual(versions);
    });

    it('should read the config of an exercise', () => {
        let received: SecurityFrameworkConfig | undefined;

        service.getConfig(42).subscribe((response) => (received = response));

        http.expectOne({ method: 'GET', url: URL }).flush(config);
        expect(received).toEqual(config);
    });

    it('should activate with the chosen version', () => {
        service.activate(42, '2.3.0').subscribe();

        const request = http.expectOne({ method: 'PUT', url: `${URL}/activate?frameworkVersion=2.3.0` });
        expect(request.request.body).toBeNull();
        request.flush(config);
    });

    it('should pin another version', () => {
        service.updateFrameworkVersion(42, '2.3.0').subscribe();

        const request = http.expectOne({ method: 'PUT', url: `${URL}/framework-version?frameworkVersion=2.3.0` });
        expect(request.request.body).toBeNull();
        request.flush(config);
    });

    it('should deactivate and add the discovered test classes without parameters', () => {
        service.deactivate(42).subscribe();
        service.addDiscoveredTestClasses(42).subscribe();

        const deactivation = http.expectOne({ method: 'PUT', url: `${URL}/deactivate` });
        const testClasses = http.expectOne({ method: 'PUT', url: `${URL}/test-classes` });
        expect(deactivation.request.params.keys()).toHaveLength(0);
        expect(testClasses.request.params.keys()).toHaveLength(0);
        deactivation.flush(config);
        testClasses.flush(config);
    });
});
